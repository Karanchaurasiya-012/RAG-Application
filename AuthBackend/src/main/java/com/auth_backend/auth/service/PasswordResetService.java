package com.auth_backend.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.auth_backend.auth.dto.request.ForgotPasswordRequest;
import com.auth_backend.auth.dto.request.ResetPasswordRequest;
import com.auth_backend.auth.entity.PasswordResetToken;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.PasswordResetTokenRepository;
import com.auth_backend.auth.repository.RefreshTokenRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.EmailNormalizer;
import com.auth_backend.auth.util.SecureTokenGenerator;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.config.AppProperties;
import com.auth_backend.notification.email.EmailDeliveryException;
import com.auth_backend.notification.email.EmailService;
import com.auth_backend.security.AuthCookieService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

	public static final String GENERIC_FORGOT_MESSAGE =
			"If an eligible account exists, password reset instructions will be sent.";
	public static final String RESET_SUCCESS_MESSAGE = "Password reset successfully.";

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final EmailService emailService;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties appProperties;
	private final AuthCookieService authCookieService;
	private final TransactionTemplate transactionTemplate;

	public PasswordResetService(
			UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			RefreshTokenRepository refreshTokenRepository,
			EmailService emailService,
			PasswordEncoder passwordEncoder,
			AppProperties appProperties,
			AuthCookieService authCookieService,
			TransactionTemplate transactionTemplate
	) {
		this.userRepository = userRepository;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.emailService = emailService;
		this.passwordEncoder = passwordEncoder;
		this.appProperties = appProperties;
		this.authCookieService = authCookieService;
		this.transactionTemplate = transactionTemplate;
	}

	public void forgotPassword(ForgotPasswordRequest request) {
		String email = EmailNormalizer.normalize(request.getEmail());
		Optional<String> rawToken = transactionTemplate.execute(status -> createResetTokenIfEligible(email));
		if (rawToken == null || rawToken.isEmpty()) {
			return;
		}

		String resetLink = UriComponentsBuilder
				.fromUriString(appProperties.getFrontendUrl())
				.path("/reset-password")
				.queryParam("token", rawToken.get())
				.build()
				.encode()
				.toUriString();

		try {
			emailService.sendPasswordResetEmail(email, resetLink);
		}
		catch (EmailDeliveryException ex) {
			log.error("Password reset email delivery failed");
			throw new BusinessException(
					"Password reset email could not be sent. Please try again later.",
					HttpStatus.SERVICE_UNAVAILABLE
			);
		}
	}

	public void resetPassword(ResetPasswordRequest request, HttpServletResponse response) {
		transactionTemplate.executeWithoutResult(status -> {
			String tokenHash = TokenHasher.sha256(request.getToken());
			PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
					.orElseThrow(this::invalidToken);

			Instant now = Instant.now();
			if (resetToken.isUsed() || resetToken.isExpired(now)) {
				throw invalidToken();
			}

			User user = resetToken.getUser();
			if (user.getStatus() == UserStatus.BLOCKED) {
				throw new BusinessException("Account is blocked.", HttpStatus.FORBIDDEN);
			}

			user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
			if (user.getStatus() == UserStatus.PENDING && user.getEmailVerifiedAt() != null) {
				user.setStatus(UserStatus.ACTIVE);
			}
			userRepository.save(user);

			resetToken.setUsedAt(now);
			passwordResetTokenRepository.save(resetToken);

			refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
		});

		authCookieService.clearAuthCookies(response);
	}

	private Optional<String> createResetTokenIfEligible(String email) {
		Optional<User> maybeUser = userRepository.findByEmail(email);
		if (maybeUser.isEmpty()) {
			log.info("Forgot-password ignored for unknown email");
			return Optional.empty();
		}

		User user = maybeUser.get();
		if (user.getStatus() == UserStatus.BLOCKED) {
			log.info("Forgot-password ignored for blocked account");
			return Optional.empty();
		}
		if (user.getPasswordHash() == null) {
			// Google-only accounts are not eligible for password reset.
			log.info("Forgot-password ignored for passwordless account");
			return Optional.empty();
		}

		String rawToken = SecureTokenGenerator.generate(32);
		Duration ttl = DurationStyle.detectAndParse(appProperties.getEmail().getPasswordResetTokenTtl());

		PasswordResetToken resetToken = new PasswordResetToken();
		resetToken.setUser(user);
		resetToken.setTokenHash(TokenHasher.sha256(rawToken));
		resetToken.setExpiresAt(Instant.now().plus(ttl));
		passwordResetTokenRepository.save(resetToken);
		return Optional.of(rawToken);
	}

	private BusinessException invalidToken() {
		return new BusinessException("Invalid or expired reset token.", HttpStatus.BAD_REQUEST);
	}
}
