package com.auth_backend.auth.service;

import java.time.Instant;
import java.util.Optional;

import com.auth_backend.auth.dto.request.ResendVerificationRequest;
import com.auth_backend.auth.entity.EmailVerificationToken;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.EmailVerificationTokenRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.EmailNormalizer;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EmailVerificationService {

	public static final String SUCCESS_MESSAGE = "Email verified successfully.";
	public static final String GENERIC_RESEND_MESSAGE =
			"If an eligible account exists, verification instructions will be sent.";

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

	private final EmailVerificationTokenRepository emailVerificationTokenRepository;
	private final UserRepository userRepository;
	private final RegistrationService registrationService;
	private final TransactionTemplate transactionTemplate;

	public EmailVerificationService(
			EmailVerificationTokenRepository emailVerificationTokenRepository,
			UserRepository userRepository,
			RegistrationService registrationService,
			TransactionTemplate transactionTemplate
	) {
		this.emailVerificationTokenRepository = emailVerificationTokenRepository;
		this.userRepository = userRepository;
		this.registrationService = registrationService;
		this.transactionTemplate = transactionTemplate;
	}

	@Transactional
	public void verifyEmail(String rawToken) {
		String tokenHash = TokenHasher.sha256(rawToken);
		EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(tokenHash)
				.orElseThrow(this::invalidToken);

		Instant now = Instant.now();
		if (verificationToken.isUsed() || verificationToken.isExpired(now)) {
			throw invalidToken();
		}

		User user = verificationToken.getUser();
		if (user.getStatus() == UserStatus.BLOCKED) {
			throw new BusinessException("Account is blocked.", HttpStatus.FORBIDDEN);
		}

		if (user.getStatus() != UserStatus.ACTIVE) {
			user.setStatus(UserStatus.ACTIVE);
		}
		if (user.getEmailVerifiedAt() == null) {
			user.setEmailVerifiedAt(now);
		}

		verificationToken.setUsedAt(now);
		userRepository.save(user);
		emailVerificationTokenRepository.save(verificationToken);
	}

	/**
	 * Enumeration-safe resend for PENDING unverified local accounts.
	 */
	public void resendVerification(ResendVerificationRequest request) {
		String email = EmailNormalizer.normalize(request.getEmail());
		Optional<String> rawToken = transactionTemplate.execute(status -> issueResendTokenIfEligible(email));
		if (rawToken == null || rawToken.isEmpty()) {
			return;
		}
		registrationService.sendVerificationEmail(email, rawToken.get());
	}

	private Optional<String> issueResendTokenIfEligible(String email) {
		Optional<User> maybeUser = userRepository.findByEmail(email);
		if (maybeUser.isEmpty()) {
			log.info("Resend-verification ignored for unknown email");
			return Optional.empty();
		}

		User user = maybeUser.get();
		if (!RegistrationService.isPendingUnverifiedLocal(user)) {
			log.info("Resend-verification ignored for ineligible account");
			return Optional.empty();
		}

		log.info("Resending verification for user id={}", user.getId());
		return Optional.of(registrationService.issueVerificationToken(user));
	}

	private BusinessException invalidToken() {
		return new BusinessException("Invalid or expired verification token.", HttpStatus.BAD_REQUEST);
	}
}
