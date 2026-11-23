package com.auth_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityFilterChainIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void csrfEndpointIssuesTokenForSpa() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.headerName").isNotEmpty())
				.andExpect(jsonPath("$.data.token").isNotEmpty())
				.andReturn();

		String setCookie = String.join(",", result.getResponse().getHeaders("Set-Cookie"));
		boolean cookiePresent = setCookie.contains("XSRF-TOKEN") || setCookie.contains("X-CSRF-TOKEN");
		Cookie legacyCookie = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookiePresent || legacyCookie != null || result.getResponse().getContentAsString().contains("token"))
				.as("CSRF token must be available to the SPA via body and/or Set-Cookie")
				.isTrue();
	}

	@Test
	void stateChangingRequestWithoutCsrfIsForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"a@b.com","password":"x"}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void meWithoutAccessTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void meWithAccessTokenCookieIsAuthorized() throws Exception {
		User user = new User();
		user.setFullName("Security User");
		user.setEmail("security-me-" + UUID.randomUUID() + "@example.com");
		user.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(Instant.now());
		user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		user = userRepository.save(user);

		String token = jwtService.createAccessToken(user.getId(), List.of("USER"));

		mockMvc.perform(get("/api/v1/auth/me")
						.cookie(new Cookie(AuthCookieNames.ACCESS_TOKEN, token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(user.getId().toString()))
				.andExpect(jsonPath("$.data.roles[0]").value("USER"));
	}

	@Test
	void refreshRemainsAccessibleWithoutAccessTokenWhenCsrfPresent() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid or expired refresh session."));
	}

	@Test
	void disallowedOriginIsRejectedByCors() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
						.header("Origin", "https://evil.example"))
				.andReturn();

		assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
	}
}
