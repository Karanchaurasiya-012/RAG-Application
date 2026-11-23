package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.RefreshTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.LoginService;
import com.auth_backend.notification.email.RecordingEmailService;
import com.auth_backend.security.AuthCookieNames;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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
class LoginIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailService recordingEmailService;

	@BeforeEach
	void clean() {
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
		recordingEmailService.clear();
	}

	@Test
	void loginSuccessSetsCookiesAndReturnsUser() throws Exception {
		User user = persistUser("login-ok@example.com", "StrongPassword123!", UserStatus.ACTIVE, Instant.now());

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"Login-OK@Example.com",
								  "password":"StrongPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value(LoginService.SUCCESS_MESSAGE))
				.andExpect(jsonPath("$.data.user.email").value("login-ok@example.com"))
				.andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
				.andReturn();

		assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())).hasSize(1);
		assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();

		String setCookie = String.join(";", result.getResponse().getHeaders("Set-Cookie"));
		assertThat(setCookie).contains(AuthCookieNames.ACCESS_TOKEN);
		assertThat(setCookie).contains(AuthCookieNames.REFRESH_TOKEN);
		assertThat(setCookie).contains("HttpOnly");
		assertThat(setCookie).contains("Secure");
		assertThat(setCookie.toLowerCase()).contains("samesite=lax");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("password");
		assertThat(result.getResponse().getContentAsString()).doesNotContain("token_hash");
	}

	@Test
	void wrongPasswordReturnsGenericUnauthorized() throws Exception {
		persistUser("login-bad@example.com", "StrongPassword123!", UserStatus.ACTIVE, Instant.now());

		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"login-bad@example.com",
								  "password":"WrongPassword123!"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(LoginService.INVALID_CREDENTIALS_MESSAGE));
	}

	@Test
	void unknownEmailReturnsGenericUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"missing@example.com",
								  "password":"StrongPassword123!"
								}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(LoginService.INVALID_CREDENTIALS_MESSAGE));
	}

	@Test
	void pendingUnverifiedUserCannotLogin() throws Exception {
		persistUser("pending@example.com", "StrongPassword123!", UserStatus.PENDING, null);

		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"pending@example.com",
								  "password":"StrongPassword123!"
								}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Email verification required."));
	}

	@Test
	void blockedUserCannotLogin() throws Exception {
		persistUser("blocked@example.com", "StrongPassword123!", UserStatus.BLOCKED, Instant.now());

		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"blocked@example.com",
								  "password":"StrongPassword123!"
								}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Account is blocked."));
	}

	@Test
	void meWorksWithAccessCookieFromLogin() throws Exception {
		persistUser("me-cookie@example.com", "StrongPassword123!", UserStatus.ACTIVE, Instant.now());

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"me-cookie@example.com",
								  "password":"StrongPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();

		Cookie accessCookie = loginResult.getResponse().getCookie(AuthCookieNames.ACCESS_TOKEN);
		assertThat(accessCookie).isNotNull();

		mockMvc.perform(get("/api/v1/auth/me").cookie(accessCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("me-cookie@example.com"))
				.andExpect(jsonPath("$.data.emailVerified").value(true));
	}

	private User persistUser(String email, String rawPassword, UserStatus status, Instant verifiedAt) {
		User user = new User();
		user.setFullName("Login User");
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(rawPassword));
		user.setStatus(status);
		user.setEmailVerifiedAt(verifiedAt);
		user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		return userRepository.save(user);
	}
}
