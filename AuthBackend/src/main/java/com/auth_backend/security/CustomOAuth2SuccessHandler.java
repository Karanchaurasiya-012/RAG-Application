package com.auth_backend.security;

import java.io.IOException;
import java.time.Instant;

import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.GoogleOAuth2Service;
import com.auth_backend.auth.service.SessionService;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(CustomOAuth2SuccessHandler.class);

	private final GoogleOAuth2Service googleOAuth2Service;
	private final SessionService sessionService;
	private final AuthCookieService authCookieService;
	private final UserRepository userRepository;
	private final AppProperties appProperties;

	public CustomOAuth2SuccessHandler(
			GoogleOAuth2Service googleOAuth2Service,
			SessionService sessionService,
			AuthCookieService authCookieService,
			UserRepository userRepository,
			AppProperties appProperties
	) {
		this.googleOAuth2Service = googleOAuth2Service;
		this.sessionService = sessionService;
		this.authCookieService = authCookieService;
		this.userRepository = userRepository;
		this.appProperties = appProperties;
	}

	@Override
	public void onAuthenticationSuccess(
			HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication
	) throws IOException {
		try {
			if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
				redirectWithError(response, GoogleOAuth2Service.ERROR_INVALID_PROFILE);
				return;
			}

			User user = googleOAuth2Service.loginOrRegister(oidcUser);
			user.setLastLoginAt(Instant.now());
			userRepository.save(user);

			SessionService.IssuedSession session = sessionService.issueSession(
					user,
					request.getHeader("User-Agent"),
					resolveClientIp(request)
			);
			authCookieService.setAccessTokenCookie(response, session.accessToken());
			authCookieService.setRefreshTokenCookie(response, session.refreshToken());

			SecurityContextHolder.clearContext();
			response.sendRedirect(appProperties.getFrontendUrl() + "/dashboard");
		}
		catch (BusinessException ex) {
			String code = extractErrorCode(ex);
			log.warn("Google OAuth login rejected: {}", ex.getMessage());
			redirectWithError(response, code);
		}
	}

	private String extractErrorCode(BusinessException ex) {
		Throwable cause = ex.getCause();
		if (cause instanceof GoogleOAuth2Service.OAuthBusinessCode oauthCode) {
			return oauthCode.getCode();
		}
		return "oauth_failed";
	}

	private void redirectWithError(HttpServletResponse response, String errorCode) throws IOException {
		SecurityContextHolder.clearContext();
		String redirect = UriComponentsBuilder
				.fromUriString(appProperties.getFrontendUrl())
				.path("/login")
				.queryParam("error", errorCode)
				.build()
				.encode()
				.toUriString();
		response.sendRedirect(redirect);
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
