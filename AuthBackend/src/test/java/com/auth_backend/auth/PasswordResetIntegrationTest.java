package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.auth_backend.auth.entity.PasswordResetToken;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.PasswordResetTokenRepository;
import com.auth_backend.auth.repository.RefreshTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.PasswordResetService;
import com.auth_backend.auth.util.SecureTokenGenerator;
import com.auth_backend.auth.util.TokenHasher;
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
class PasswordResetIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailService recordingEmailService;

	@BeforeEach
	void clean() {
		refreshTokenRepository.deleteAll();
		passwordResetTokenRepository.deleteAll();
		userRepository.deleteAll();
		recordingEmailService.clear();
	}

	@Test
	void forgotPasswordSendsEmailForEligibleLocalAccount() throws Exception {
		persistLocalUser("reset-me@example.com", "OldPassword123!", UserStatus.ACTIVE, Instant.now());

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"Reset-Me@Example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.GENERIC_FORGOT_MESSAGE));

		assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages().get(0).getHtmlBody())
				.contains("/reset-password?token=");
	}

	@Test
	void forgotPasswordIsGenericForUnknownAndGoogleOnlyAccounts() throws Exception {
		User googleOnly = new User();
		googleOnly.setFullName("Google Only");
		googleOnly.setEmail("google-only@example.com");
		googleOnly.setPasswordHash(null);
		googleOnly.setStatus(UserStatus.ACTIVE);
		googleOnly.setEmailVerifiedAt(Instant.now());
		googleOnly.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		userRepository.save(googleOnly);

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"missing@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.GENERIC_FORGOT_MESSAGE));

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"google-only@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.GENERIC_FORGOT_MESSAGE));

		assertThat(passwordResetTokenRepository.findAll()).isEmpty();
		assertThat(recordingEmailService.getSentMessages()).isEmpty();
	}

	@Test
	void resetPasswordUpdatesHashRevokesSessionsAndConsumesToken() throws Exception {
		User user = persistLocalUser("do-reset@example.com", "OldPassword123!", UserStatus.ACTIVE, Instant.now());

		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"do-reset@example.com",
								  "password":"OldPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andReturn();
		Cookie refreshCookie = loginResult.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
		assertThat(refreshCookie).isNotNull();
		assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())).hasSize(1);

		String rawToken = SecureTokenGenerator.generate(32);
		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setUser(user);
		resetToken.setTokenHash(TokenHasher.sha256(rawToken));
		resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
		passwordResetTokenRepository.save(resetToken);

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "token":"%s",
								  "newPassword":"NewStrongPassword123!"
								}
								""".formatted(rawToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(PasswordResetService.RESET_SUCCESS_MESSAGE));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("NewStrongPassword123!", reloaded.getPasswordHash())).isTrue();
		assertThat(passwordEncoder.matches("OldPassword123!", reloaded.getPasswordHash())).isFalse();
		assertThat(passwordResetTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken)).orElseThrow().getUsedAt())
				.isNotNull();
		assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();

		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"do-reset@example.com",
								  "password":"NewStrongPassword123!"
								}
								"""))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidExpiredAndReusedResetTokens() throws Exception {
		User user = persistLocalUser("bad-token@example.com", "OldPassword123!", UserStatus.ACTIVE, Instant.now());

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "token":"not-real",
								  "newPassword":"NewStrongPassword123!"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid or expired reset token."));

		String expiredRaw = SecureTokenGenerator.generate(32);
		PasswordResetToken expired = new PasswordResetToken();
		expired.setUser(user);
		expired.setTokenHash(TokenHasher.sha256(expiredRaw));
		expired.setExpiresAt(Instant.now().minusSeconds(30));
		passwordResetTokenRepository.save(expired);

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "token":"%s",
								  "newPassword":"NewStrongPassword123!"
								}
								""".formatted(expiredRaw)))
				.andExpect(status().isBadRequest());

		String usedRaw = SecureTokenGenerator.generate(32);
		PasswordResetToken used = new PasswordResetToken();
		used.setUser(user);
		used.setTokenHash(TokenHasher.sha256(usedRaw));
		used.setExpiresAt(Instant.now().plusSeconds(3600));
		used.setUsedAt(Instant.now().minusSeconds(10));
		passwordResetTokenRepository.save(used);

		mockMvc.perform(post("/api/v1/auth/reset-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "token":"%s",
								  "newPassword":"NewStrongPassword123!"
								}
								""".formatted(usedRaw)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void smtpFailureOnForgotPasswordReturnsServiceUnavailable() throws Exception {
		persistLocalUser("smtp-reset@example.com", "OldPassword123!", UserStatus.ACTIVE, Instant.now());
		recordingEmailService.failNextSend();

		mockMvc.perform(post("/api/v1/auth/forgot-password")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"smtp-reset@example.com"}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false));

		assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages()).isEmpty();
	}

	private User persistLocalUser(String email, String password, UserStatus status, Instant verifiedAt) {
		User user = new User();
		user.setFullName("Reset User");
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setStatus(status);
		user.setEmailVerifiedAt(verifiedAt);
		user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		return userRepository.save(user);
	}
}
