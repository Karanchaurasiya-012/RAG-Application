package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.auth_backend.auth.entity.EmailVerificationToken;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.EmailVerificationTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.EmailVerificationService;
import com.auth_backend.auth.util.SecureTokenGenerator;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.notification.email.RecordingEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmailVerificationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private RecordingEmailService recordingEmailService;

	@BeforeEach
	void setUp() {
		emailVerificationTokenRepository.deleteAll();
		userRepository.deleteAll();
		recordingEmailService.clear();
	}

	@Test
	void verifiesValidTokenAndActivatesUser() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		User user = persistPendingUserWithToken(rawToken, Instant.now().plusSeconds(3600), null);

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value(EmailVerificationService.SUCCESS_MESSAGE));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(reloaded.getEmailVerifiedAt()).isNotNull();

		EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken)).orElseThrow();
		assertThat(token.getUsedAt()).isNotNull();
	}

	@Test
	void rejectsInvalidToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"not-a-real-token"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Invalid or expired verification token."));
	}

	@Test
	void rejectsExpiredToken() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		persistPendingUserWithToken(rawToken, Instant.now().minusSeconds(60), null);

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid or expired verification token."));
	}

	@Test
	void rejectsReusedToken() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		persistPendingUserWithToken(rawToken, Instant.now().plusSeconds(3600), Instant.now().minusSeconds(10));

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid or expired verification token."));
	}

	@Test
	void alreadyVerifiedUserWithFreshTokenStillSucceedsIdempotently() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		User user = persistPendingUserWithToken(rawToken, Instant.now().plusSeconds(3600), null);
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(Instant.now().minusSeconds(120));
		userRepository.saveAndFlush(user);

		mockMvc.perform(post("/api/v1/auth/verify-email")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(EmailVerificationService.SUCCESS_MESSAGE));

		EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken)).orElseThrow();
		assertThat(token.getUsedAt()).isNotNull();
	}

	@Test
	void resendVerificationSendsNewEmailForPendingUser() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		User user = persistPendingUserWithToken(rawToken, Instant.now().plusSeconds(3600), null);
		user.setEmail("resend-me@example.com");
		userRepository.save(user);
		recordingEmailService.clear();

		mockMvc.perform(post("/api/v1/auth/resend-verification")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"Resend-Me@Example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

		assertThat(recordingEmailService.getSentMessages()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages().get(0).getHtmlBody())
				.contains("/verify-email?token=");
		assertThat(emailVerificationTokenRepository.findByUserId(user.getId()))
				.filteredOn(token -> token.getUsedAt() == null)
				.hasSize(1);
		assertThat(emailVerificationTokenRepository.findByTokenHash(TokenHasher.sha256(rawToken)).orElseThrow().getUsedAt())
				.isNotNull();
	}

	@Test
	void resendVerificationIsGenericForUnknownAndVerifiedAccounts() throws Exception {
		String rawToken = SecureTokenGenerator.generate(32);
		User user = persistPendingUserWithToken(rawToken, Instant.now().plusSeconds(3600), null);
		user.setEmail("already-verified@example.com");
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(Instant.now());
		userRepository.save(user);
		recordingEmailService.clear();

		mockMvc.perform(post("/api/v1/auth/resend-verification")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"missing@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

		mockMvc.perform(post("/api/v1/auth/resend-verification")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"already-verified@example.com"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(EmailVerificationService.GENERIC_RESEND_MESSAGE));

		assertThat(recordingEmailService.getSentMessages()).isEmpty();
	}

	private User persistPendingUserWithToken(String rawToken, Instant expiresAt, Instant usedAt) {
		User user = new User();
		user.setFullName("Verify User");
		user.setEmail("verify-" + UUID.randomUUID() + "@example.com");
		user.setPasswordHash("argon2-hash");
		user.setStatus(UserStatus.PENDING);
		user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		User savedUser = userRepository.save(user);

		EmailVerificationToken token = new EmailVerificationToken();
		token.setUser(savedUser);
		token.setTokenHash(TokenHasher.sha256(rawToken));
		token.setExpiresAt(expiresAt);
		token.setUsedAt(usedAt);
		emailVerificationTokenRepository.save(token);
		emailVerificationTokenRepository.flush();
		return savedUser;
	}
}
