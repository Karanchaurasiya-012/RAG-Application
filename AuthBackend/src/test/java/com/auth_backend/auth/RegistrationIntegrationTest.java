package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.EmailVerificationTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.RegistrationService;
import com.auth_backend.notification.email.RecordingEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailService recordingEmailService;

	@BeforeEach
	void cleanUsers() {
		emailVerificationTokenRepository.deleteAll();
		userRepository.deleteAll();
		recordingEmailService.clear();
	}

	@Test
	void registersValidUserAsPendingWithUserRoleHashedPasswordAndVerificationEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Rahul Sharma",
								  "email": "Rahul@Example.com",
								  "password": "StrongPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value(RegistrationService.GENERIC_REGISTER_MESSAGE))
				.andExpect(jsonPath("$.data").doesNotExist());

		User user = userRepository.findWithRolesByEmail("rahul@example.com").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Rahul Sharma");
		assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
		assertThat(user.getEmailVerifiedAt()).isNull();
		assertThat(user.getPasswordHash()).isNotEqualTo("StrongPassword123!");
		assertThat(user.getPasswordHash().toLowerCase()).contains("argon2");
		assertThat(passwordEncoder.matches("StrongPassword123!", user.getPasswordHash())).isTrue();
		assertThat(user.getRoles())
				.extracting(role -> role.getName())
				.containsExactly(RoleName.USER);

		assertThat(emailVerificationTokenRepository.findByUserId(user.getId())).hasSize(1);
		assertThat(recordingEmailService.getSentMessages()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages().get(0).getTo()).isEqualTo("rahul@example.com");
		assertThat(recordingEmailService.getSentMessages().get(0).getHtmlBody())
				.contains("/verify-email?token=");
	}

	@Test
	void rejectsInvalidEmail() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Rahul Sharma",
								  "email": "not-an-email",
								  "password": "StrongPassword123!"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data.email").exists());
	}

	@Test
	void rejectsWeakPassword() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Rahul Sharma",
								  "email": "weak@example.com",
								  "password": "short"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data.password").exists());

		assertThat(userRepository.existsByEmail("weak@example.com")).isFalse();
	}

	@Test
	void pendingDuplicateEmailResendsVerificationAndUpdatesCredentials() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "First User",
								  "email": "dup@example.com",
								  "password": "StrongPassword123!"
								}
								"""))
				.andExpect(status().isOk());

		User first = userRepository.findByEmail("dup@example.com").orElseThrow();
		assertThat(emailVerificationTokenRepository.findByUserId(first.getId())).hasSize(1);
		recordingEmailService.clear();

		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Second User",
								  "email": "DUP@example.com",
								  "password": "AnotherPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value(RegistrationService.GENERIC_REGISTER_MESSAGE));

		assertThat(userRepository.findAll()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages()).hasSize(1);
		User user = userRepository.findByEmail("dup@example.com").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Second User");
		assertThat(passwordEncoder.matches("AnotherPassword123!", user.getPasswordHash())).isTrue();
		assertThat(emailVerificationTokenRepository.findByUserId(user.getId())).hasSize(2);
		assertThat(emailVerificationTokenRepository.findByUserId(user.getId()))
				.filteredOn(token -> token.getUsedAt() == null)
				.hasSize(1);
	}

	@Test
	void verifiedDuplicateEmailReturnsGenericSuccessWithoutResend() throws Exception {
		User existing = new User();
		existing.setFullName("Verified User");
		existing.setEmail("verified-dup@example.com");
		existing.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
		existing.setStatus(UserStatus.ACTIVE);
		existing.setEmailVerifiedAt(Instant.now());
		existing.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		userRepository.save(existing);
		recordingEmailService.clear();

		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Attacker",
								  "email": "verified-dup@example.com",
								  "password": "AnotherPassword123!"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(RegistrationService.GENERIC_REGISTER_MESSAGE));

		assertThat(userRepository.findAll()).hasSize(1);
		assertThat(recordingEmailService.getSentMessages()).isEmpty();
		User user = userRepository.findByEmail("verified-dup@example.com").orElseThrow();
		assertThat(user.getFullName()).isEqualTo("Verified User");
		assertThat(passwordEncoder.matches("StrongPassword123!", user.getPasswordHash())).isTrue();
	}

	@Test
	void smtpFailureReturnsServiceUnavailableAndDoesNotPretendEmailWasSent() throws Exception {
		recordingEmailService.failNextSend();

		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "SMTP Fail",
								  "email": "smtp-fail@example.com",
								  "password": "StrongPassword123!"
								}
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("Verification email could not be sent. Please try again later."));

		assertThat(userRepository.existsByEmail("smtp-fail@example.com")).isTrue();
		assertThat(recordingEmailService.getSentMessages()).isEmpty();
	}
}
