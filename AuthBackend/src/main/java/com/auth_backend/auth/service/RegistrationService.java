package com.auth_backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.auth_backend.auth.dto.request.RegisterRequest;
import com.auth_backend.auth.entity.EmailVerificationToken;
import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.EmailVerificationTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.EmailNormalizer;
import com.auth_backend.auth.util.SecureTokenGenerator;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.config.AppProperties;
import com.auth_backend.notification.email.EmailDeliveryException;
import com.auth_backend.notification.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class RegistrationService {

	public static final String GENERIC_REGISTER_MESSAGE =
			"If registration is accepted, check your email for verification instructions.";

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final AppProperties appProperties;
	private final TransactionTemplate transactionTemplate;

	public RegistrationService(
			UserRepository userRepository,
			RoleRepository roleRepository,
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			PasswordEncoder passwordEncoder,
			EmailService emailService,
			AppProperties appProperties,
			TransactionTemplate transactionTemplate
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
		this.appProperties = appProperties;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Registers a local email/password user as PENDING with default USER role,
	 * stores a hashed verification token, then attempts SMTP delivery.
	 * Duplicate verified/active emails return the same generic outcome.
	 * Duplicate PENDING unverified local accounts refresh credentials and resend verification.
	 */
	public void register(RegisterRequest request) {
		String email = EmailNormalizer.normalize(request.getEmail());
		Optional<String> rawToken = transactionTemplate.execute(status -> createPendingRegistration(request, email));
		if (rawToken == null || rawToken.isEmpty()) {
			return;
		}

		sendVerificationEmail(email, rawToken.get());
	}

	private Optional<String> createPendingRegistration(RegisterRequest request, String email) {
		Optional<User> existing = userRepository.findByEmail(email);
		if (existing.isPresent()) {
			User user = existing.get();
			if (isPendingUnverifiedLocal(user)) {
				String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
				user.setFullName(fullName);
				user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
				userRepository.save(user);
				log.info("Re-issued verification for pending user id={}", user.getId());
				return Optional.of(issueVerificationToken(user));
			}
			log.info("Registration ignored for existing email");
			return Optional.empty();
		}

		Role userRole = roleRepository.findByName(RoleName.USER)
				.orElseThrow(() -> new BusinessException("Default USER role is not configured.", HttpStatus.INTERNAL_SERVER_ERROR));

		String fullName = request.getFullName() == null ? "" : request.getFullName().trim();
		User user = new User();
		user.setFullName(fullName);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setStatus(UserStatus.PENDING);
		user.addRole(userRole);
		userRepository.save(user);

		log.info("Registered pending user id={}", user.getId());
		return Optional.of(issueVerificationToken(user));
	}

	static boolean isPendingUnverifiedLocal(User user) {
		return user.getStatus() == UserStatus.PENDING
				&& user.getEmailVerifiedAt() == null
				&& user.getPasswordHash() != null;
	}

	String issueVerificationToken(User user) {
		Instant now = Instant.now();
		for (EmailVerificationToken old : emailVerificationTokenRepository.findByUserId(user.getId())) {
			if (!old.isUsed()) {
				old.setUsedAt(now);
				emailVerificationTokenRepository.save(old);
			}
		}

		String rawToken = SecureTokenGenerator.generate(32);
		Duration ttl = DurationStyle.detectAndParse(appProperties.getEmail().getVerificationTokenTtl());

		EmailVerificationToken verificationToken = new EmailVerificationToken();
		verificationToken.setUser(user);
		verificationToken.setTokenHash(TokenHasher.sha256(rawToken));
		verificationToken.setExpiresAt(now.plus(ttl));
		emailVerificationTokenRepository.save(verificationToken);
		return rawToken;
	}

	void sendVerificationEmail(String email, String rawToken) {
		String verificationLink = UriComponentsBuilder
				.fromUriString(appProperties.getFrontendUrl())
				.path("/verify-email")
				.queryParam("token", rawToken)
				.build()
				.encode()
				.toUriString();

		try {
			emailService.sendVerificationEmail(email, verificationLink);
		}
		catch (EmailDeliveryException ex) {
			log.error("Verification email delivery failed for registered user");
			throw new BusinessException(
					"Verification email could not be sent. Please try again later.",
					HttpStatus.SERVICE_UNAVAILABLE
			);
		}
	}
}
