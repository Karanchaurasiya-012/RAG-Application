package com.auth_backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.auth_backend.auth.entity.AuthIdentity;
import com.auth_backend.auth.entity.AuthProvider;
import com.auth_backend.auth.entity.EmailVerificationToken;
import com.auth_backend.auth.entity.PasswordResetToken;
import com.auth_backend.auth.entity.RefreshToken;
import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class AuthRepositoryIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private AuthIdentityRepository authIdentityRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokenRepository;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@Test
	void seededRolesExist() {
		assertThat(roleRepository.findByName(RoleName.USER)).isPresent();
		assertThat(roleRepository.findByName(RoleName.ADMIN)).isPresent();
	}

	@Test
	void createUserWithDefaultRoleAndLookupByEmail() {
		Role userRole = roleRepository.findByName(RoleName.USER).orElseThrow();

		User user = new User();
		user.setFullName("Rahul Sharma");
		user.setEmail("rahul@example.com");
		user.setPasswordHash("argon2-hash-placeholder");
		user.setStatus(UserStatus.PENDING);
		user.addRole(userRole);

		User saved = userRepository.saveAndFlush(user);

		assertThat(saved.getId()).isNotNull();
		assertThat(userRepository.existsByEmail("rahul@example.com")).isTrue();

		User loaded = userRepository.findWithRolesByEmail("rahul@example.com").orElseThrow();
		assertThat(loaded.getRoles())
				.extracting(Role::getName)
				.containsExactly(RoleName.USER);
	}

	@Test
	void duplicateEmailIsRejected() {
		persistPendingUser("dup@example.com", "hash-1");

		User duplicate = new User();
		duplicate.setFullName("Other User");
		duplicate.setEmail("dup@example.com");
		duplicate.setPasswordHash("hash-2");
		duplicate.setStatus(UserStatus.PENDING);

		assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void googleIdentityIsUniqueByProviderAndSubject() {
		User user = persistPendingUser("google-user@example.com", null);

		AuthIdentity identity = new AuthIdentity();
		identity.setUser(user);
		identity.setProvider(AuthProvider.GOOGLE);
		identity.setProviderSubject("google-sub-123");
		identity.setProviderEmail("google-user@example.com");
		authIdentityRepository.saveAndFlush(identity);

		assertThat(authIdentityRepository
				.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-123"))
				.isPresent();

		AuthIdentity duplicate = new AuthIdentity();
		duplicate.setUser(user);
		duplicate.setProvider(AuthProvider.GOOGLE);
		duplicate.setProviderSubject("google-sub-123");
		duplicate.setProviderEmail("google-user@example.com");

		assertThatThrownBy(() -> authIdentityRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void refreshTokenCanBeStoredHashedAndRevokedByFamily() {
		User user = persistPendingUser("session@example.com", "hash");

		UUID familyId = UUID.randomUUID();
		RefreshToken token = new RefreshToken();
		token.setUser(user);
		token.setTokenHash("sha256-refresh-hash-1");
		token.setFamilyId(familyId);
		token.setExpiresAt(Instant.now().plusSeconds(3600));
		token.setIpAddress("127.0.0.1");
		refreshTokenRepository.saveAndFlush(token);

		assertThat(refreshTokenRepository.findByTokenHash("sha256-refresh-hash-1")).isPresent();

		int revoked = refreshTokenRepository.revokeAllActiveForFamily(familyId, Instant.now());
		assertThat(revoked).isEqualTo(1);

		RefreshToken reloaded = refreshTokenRepository.findByTokenHash("sha256-refresh-hash-1").orElseThrow();
		assertThat(reloaded.isRevoked()).isTrue();
	}

	@Test
	void oneTimeTokensStoreHashOnly() {
		User user = persistPendingUser("tokens@example.com", "hash");

		EmailVerificationToken verificationToken = new EmailVerificationToken();
		verificationToken.setUser(user);
		verificationToken.setTokenHash("verify-hash-1");
		verificationToken.setExpiresAt(Instant.now().plusSeconds(1800));
		emailVerificationTokenRepository.saveAndFlush(verificationToken);

		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setUser(user);
		resetToken.setTokenHash("reset-hash-1");
		resetToken.setExpiresAt(Instant.now().plusSeconds(1800));
		passwordResetTokenRepository.saveAndFlush(resetToken);

		assertThat(emailVerificationTokenRepository.findByTokenHash("verify-hash-1")).isPresent();
		assertThat(passwordResetTokenRepository.findByTokenHash("reset-hash-1")).isPresent();
	}

	private User persistPendingUser(String email, String passwordHash) {
		User user = new User();
		user.setFullName("Test User");
		user.setEmail(email);
		user.setPasswordHash(passwordHash);
		user.setStatus(UserStatus.PENDING);
		return userRepository.saveAndFlush(user);
	}
}
