package com.auth_backend.auth.service;

import java.time.Instant;

import com.auth_backend.auth.entity.AuthIdentity;
import com.auth_backend.auth.entity.AuthProvider;
import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.AuthIdentityRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.EmailNormalizer;
import com.auth_backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class GoogleOAuth2Service {

	public static final String ERROR_UNVERIFIED_EMAIL = "google_email_unverified";
	public static final String ERROR_ACCOUNT_EXISTS = "account_exists_use_password_or_link";
	public static final String ERROR_ACCOUNT_BLOCKED = "account_blocked";
	public static final String ERROR_INVALID_PROFILE = "google_profile_invalid";

	private final AuthIdentityRepository authIdentityRepository;
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	public GoogleOAuth2Service(
			AuthIdentityRepository authIdentityRepository,
			UserRepository userRepository,
			RoleRepository roleRepository
	) {
		this.authIdentityRepository = authIdentityRepository;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
	}

	/**
	 * Resolves an application user from a validated Google OIDC identity.
	 * Existing local accounts are never auto-linked by email alone.
	 */
	@Transactional
	public User loginOrRegister(OidcUser oidcUser) {
		String subject = oidcUser.getSubject();
		String email = EmailNormalizer.normalize(oidcUser.getEmail());
		Boolean emailVerified = oidcUser.getEmailVerified();
		String fullName = resolveFullName(oidcUser, email);

		if (!StringUtils.hasText(subject) || !StringUtils.hasText(email)) {
			throw oauthError(ERROR_INVALID_PROFILE, "Google profile is missing required identity claims.");
		}
		if (emailVerified == null || !emailVerified) {
			throw oauthError(ERROR_UNVERIFIED_EMAIL, "Google email is not verified.");
		}

		return authIdentityRepository.findByProviderAndProviderSubject(AuthProvider.GOOGLE, subject)
				.map(identity -> loadActiveUser(identity.getUser().getId()))
				.orElseGet(() -> createGoogleUser(subject, email, fullName));
	}

	private User createGoogleUser(String subject, String email, String fullName) {
		if (userRepository.existsByEmail(email)) {
			throw oauthError(
					ERROR_ACCOUNT_EXISTS,
					"An account with this email already exists. Sign in with password and link Google explicitly."
			);
		}

		Role userRole = roleRepository.findByName(RoleName.USER)
				.orElseThrow(() -> new BusinessException("Default USER role is not configured.", HttpStatus.INTERNAL_SERVER_ERROR));

		User user = new User();
		user.setFullName(fullName);
		user.setEmail(email);
		user.setPasswordHash(null);
		user.setStatus(UserStatus.ACTIVE);
		user.setEmailVerifiedAt(Instant.now());
		user.addRole(userRole);
		userRepository.save(user);

		AuthIdentity identity = new AuthIdentity();
		identity.setUser(user);
		identity.setProvider(AuthProvider.GOOGLE);
		identity.setProviderSubject(subject);
		identity.setProviderEmail(email);
		authIdentityRepository.save(identity);

		return userRepository.findWithRolesById(user.getId()).orElse(user);
	}

	private User loadActiveUser(java.util.UUID userId) {
		User user = userRepository.findWithRolesById(userId)
				.orElseThrow(() -> oauthError(ERROR_INVALID_PROFILE, "Linked application user was not found."));

		if (user.getStatus() == UserStatus.BLOCKED) {
			throw oauthError(ERROR_ACCOUNT_BLOCKED, "Account is blocked.");
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			user.setStatus(UserStatus.ACTIVE);
		}
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(Instant.now());
		}
		user.setLastLoginAt(Instant.now());
		return userRepository.save(user);
	}

	private String resolveFullName(OidcUser oidcUser, String email) {
		if (StringUtils.hasText(oidcUser.getFullName())) {
			return truncate(oidcUser.getFullName(), 100);
		}
		if (StringUtils.hasText(oidcUser.getGivenName())) {
			return truncate(oidcUser.getGivenName(), 100);
		}
		return truncate(email, 100);
	}

	private String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}

	private BusinessException oauthError(String code, String message) {
		BusinessException exception = new BusinessException(message, HttpStatus.UNAUTHORIZED);
		exception.initCause(new OAuthBusinessCode(code));
		return exception;
	}

	/**
	 * Marker cause carrying a stable frontend error code.
	 */
	public static final class OAuthBusinessCode extends RuntimeException {
		private final String code;

		public OAuthBusinessCode(String code) {
			super(code);
			this.code = code;
		}

		public String getCode() {
			return code;
		}
	}
}
