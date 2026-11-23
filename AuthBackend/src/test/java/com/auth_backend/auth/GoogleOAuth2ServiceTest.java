package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import com.auth_backend.auth.entity.AuthIdentity;
import com.auth_backend.auth.entity.AuthProvider;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.AuthIdentityRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.GoogleOAuth2Service;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.notification.email.RecordingEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GoogleOAuth2ServiceTest {

	@Autowired
	private GoogleOAuth2Service googleOAuth2Service;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private AuthIdentityRepository authIdentityRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private RecordingEmailService recordingEmailService;

	@BeforeEach
	void clean() {
		authIdentityRepository.deleteAll();
		userRepository.deleteAll();
		recordingEmailService.clear();
	}

	@Test
	void createsNewActiveGoogleUserWhenIdentityUnknown() {
		OidcUser oidcUser = oidcUser("google-sub-1", "new-google@example.com", true, "Google User");

		User user = googleOAuth2Service.loginOrRegister(oidcUser);

		assertThat(user.getEmail()).isEqualTo("new-google@example.com");
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.getEmailVerifiedAt()).isNotNull();
		assertThat(user.getRoles()).extracting(role -> role.getName()).containsExactly(RoleName.USER);
		assertThat(authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "google-sub-1")).isPresent();
	}

	@Test
	void logsInExistingGoogleIdentity() {
		User existing = new User();
		existing.setFullName("Existing Google");
		existing.setEmail("existing-google@example.com");
		existing.setStatus(UserStatus.ACTIVE);
		existing.setEmailVerifiedAt(Instant.now());
		existing.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		existing = userRepository.save(existing);

		AuthIdentity identity = new AuthIdentity();
		identity.setUser(existing);
		identity.setProvider(AuthProvider.GOOGLE);
		identity.setProviderSubject("google-sub-existing");
		identity.setProviderEmail("existing-google@example.com");
		authIdentityRepository.save(identity);

		User loggedIn = googleOAuth2Service.loginOrRegister(
				oidcUser("google-sub-existing", "existing-google@example.com", true, "Existing Google")
		);

		assertThat(loggedIn.getId()).isEqualTo(existing.getId());
		assertThat(authIdentityRepository.findAll()).hasSize(1);
	}

	@Test
	void rejectsUnverifiedGoogleEmail() {
		OidcUser oidcUser = oidcUser("google-sub-unverified", "unverified@example.com", false, "Nope");

		assertThatThrownBy(() -> googleOAuth2Service.loginOrRegister(oidcUser))
				.isInstanceOf(BusinessException.class)
				.satisfies(ex -> {
					BusinessException businessException = (BusinessException) ex;
					assertThat(businessException.getCause()).isInstanceOf(GoogleOAuth2Service.OAuthBusinessCode.class);
					assertThat(((GoogleOAuth2Service.OAuthBusinessCode) businessException.getCause()).getCode())
							.isEqualTo(GoogleOAuth2Service.ERROR_UNVERIFIED_EMAIL);
				});
	}

	@Test
	void doesNotAutoLinkExistingLocalEmailAccount() {
		User local = new User();
		local.setFullName("Local User");
		local.setEmail("local@example.com");
		local.setPasswordHash(passwordEncoder.encode("StrongPassword123!"));
		local.setStatus(UserStatus.ACTIVE);
		local.setEmailVerifiedAt(Instant.now());
		local.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		userRepository.save(local);

		OidcUser oidcUser = oidcUser("google-sub-local-email", "local@example.com", true, "Google Name");

		assertThatThrownBy(() -> googleOAuth2Service.loginOrRegister(oidcUser))
				.isInstanceOf(BusinessException.class)
				.satisfies(ex -> assertThat(((GoogleOAuth2Service.OAuthBusinessCode) ex.getCause()).getCode())
						.isEqualTo(GoogleOAuth2Service.ERROR_ACCOUNT_EXISTS));

		assertThat(authIdentityRepository.findAll()).isEmpty();
	}

	@Test
	void rejectsBlockedLinkedUser() {
		User blocked = new User();
		blocked.setFullName("Blocked");
		blocked.setEmail("blocked-google@example.com");
		blocked.setStatus(UserStatus.BLOCKED);
		blocked.setEmailVerifiedAt(Instant.now());
		blocked.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
		blocked = userRepository.save(blocked);

		AuthIdentity identity = new AuthIdentity();
		identity.setUser(blocked);
		identity.setProvider(AuthProvider.GOOGLE);
		identity.setProviderSubject("google-sub-blocked");
		identity.setProviderEmail("blocked-google@example.com");
		authIdentityRepository.save(identity);

		assertThatThrownBy(() -> googleOAuth2Service.loginOrRegister(
				oidcUser("google-sub-blocked", "blocked-google@example.com", true, "Blocked")
		)).isInstanceOf(BusinessException.class)
				.satisfies(ex -> assertThat(((GoogleOAuth2Service.OAuthBusinessCode) ex.getCause()).getCode())
						.isEqualTo(GoogleOAuth2Service.ERROR_ACCOUNT_BLOCKED));
	}

	private OidcUser oidcUser(String subject, String email, boolean emailVerified, String name) {
		Map<String, Object> claims = Map.of(
				"sub", subject,
				"email", email,
				"email_verified", emailVerified,
				"name", name
		);
		OidcIdToken idToken = new OidcIdToken(
				"token-value",
				Instant.now(),
				Instant.now().plusSeconds(3600),
				claims
		);
		return new DefaultOidcUser(java.util.List.of(new OidcUserAuthority(idToken)), idToken);
	}
}
