package com.auth_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.GoogleOAuth2Service;
import com.auth_backend.auth.service.SessionService;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

class CustomOAuth2SuccessHandlerTest {

	@Test
	void redirectsToDashboardAndSetsSessionOnSuccess() throws Exception {
		GoogleOAuth2Service googleOAuth2Service = mock(GoogleOAuth2Service.class);
		SessionService sessionService = mock(SessionService.class);
		AuthCookieService authCookieService = mock(AuthCookieService.class);
		UserRepository userRepository = mock(UserRepository.class);
		AppProperties appProperties = new AppProperties();
		appProperties.setFrontendUrl("http://localhost:3000");

		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("google@example.com");
		user.setFullName("Google User");
		user.setStatus(UserStatus.ACTIVE);
		Role role = new Role();
		role.setName(RoleName.USER);
		user.addRole(role);

		when(googleOAuth2Service.loginOrRegister(any())).thenReturn(user);
		when(userRepository.save(any(User.class))).thenReturn(user);
		when(sessionService.issueSession(eq(user), any(), any()))
				.thenReturn(new SessionService.IssuedSession("access", "refresh"));

		CustomOAuth2SuccessHandler handler = new CustomOAuth2SuccessHandler(
				googleOAuth2Service, sessionService, authCookieService, userRepository, appProperties
		);

		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
				oidcUser("sub-1", "google@example.com"),
				List.of(),
				"google"
		);

		handler.onAuthenticationSuccess(request, response, authentication);

		verify(authCookieService).setAccessTokenCookie(response, "access");
		verify(authCookieService).setRefreshTokenCookie(response, "refresh");
		assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/dashboard");
	}

	@Test
	void redirectsToLoginWithStableErrorCodeOnBusinessFailure() throws Exception {
		GoogleOAuth2Service googleOAuth2Service = mock(GoogleOAuth2Service.class);
		SessionService sessionService = mock(SessionService.class);
		AuthCookieService authCookieService = mock(AuthCookieService.class);
		UserRepository userRepository = mock(UserRepository.class);
		AppProperties appProperties = new AppProperties();
		appProperties.setFrontendUrl("http://localhost:3000");

		BusinessException rejected = new BusinessException("exists", HttpStatus.UNAUTHORIZED);
		rejected.initCause(new GoogleOAuth2Service.OAuthBusinessCode(GoogleOAuth2Service.ERROR_ACCOUNT_EXISTS));
		when(googleOAuth2Service.loginOrRegister(any())).thenThrow(rejected);

		CustomOAuth2SuccessHandler handler = new CustomOAuth2SuccessHandler(
				googleOAuth2Service, sessionService, authCookieService, userRepository, appProperties
		);

		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
				oidcUser("sub-2", "local@example.com"),
				List.of(),
				"google"
		);

		handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:3000/login?error=account_exists_use_password_or_link");
	}

	@Test
	void rejectsNonOidcPrincipal() throws Exception {
		CustomOAuth2SuccessHandler handler = new CustomOAuth2SuccessHandler(
				mock(GoogleOAuth2Service.class),
				mock(SessionService.class),
				mock(AuthCookieService.class),
				mock(UserRepository.class),
				new AppProperties() {{
					setFrontendUrl("http://localhost:3000");
				}}
		);

		MockHttpServletResponse response = new MockHttpServletResponse();
		handler.onAuthenticationSuccess(
				new MockHttpServletRequest(),
				response,
				new UsernamePasswordAuthenticationToken("user", "pass")
		);

		assertThat(response.getRedirectedUrl())
				.isEqualTo("http://localhost:3000/login?error=google_profile_invalid");
	}

	private DefaultOidcUser oidcUser(String subject, String email) {
		Map<String, Object> claims = Map.of(
				"sub", subject,
				"email", email,
				"email_verified", true,
				"name", "Name"
		);
		OidcIdToken idToken = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60), claims);
		return new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
	}
}
