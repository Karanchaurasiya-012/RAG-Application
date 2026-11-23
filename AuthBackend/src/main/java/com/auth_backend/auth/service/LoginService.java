package com.auth_backend.auth.service;

import java.time.Instant;

import com.auth_backend.auth.dto.request.LoginRequest;
import com.auth_backend.auth.dto.response.AuthResponse;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.mapper.UserMapper;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.EmailNormalizer;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.security.AuthCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

	public static final String SUCCESS_MESSAGE = "Login successful.";
	public static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final SessionService sessionService;
	private final AuthCookieService authCookieService;
	private final UserMapper userMapper;

	public LoginService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			SessionService sessionService,
			AuthCookieService authCookieService,
			UserMapper userMapper
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.sessionService = sessionService;
		this.authCookieService = authCookieService;
		this.userMapper = userMapper;
	}

	@Transactional
	public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		String email = EmailNormalizer.normalize(request.getEmail());
		User user = userRepository.findWithRolesByEmail(email)
				.orElseThrow(this::invalidCredentials);

		if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
			throw invalidCredentials();
		}

		if (user.getStatus() == UserStatus.BLOCKED) {
			throw new BusinessException("Account is blocked.", HttpStatus.FORBIDDEN);
		}

		if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
			throw new BusinessException("Email verification required.", HttpStatus.FORBIDDEN);
		}

		SessionService.IssuedSession session = sessionService.issueSession(
				user,
				httpRequest.getHeader("User-Agent"),
				resolveClientIp(httpRequest)
		);

		authCookieService.setAccessTokenCookie(httpResponse, session.accessToken());
		authCookieService.setRefreshTokenCookie(httpResponse, session.refreshToken());

		user.setLastLoginAt(Instant.now());
		userRepository.save(user);

		return new AuthResponse(userMapper.toResponse(user));
	}

	private BusinessException invalidCredentials() {
		return new BusinessException(INVALID_CREDENTIALS_MESSAGE, HttpStatus.UNAUTHORIZED);
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded;
		}
		return request.getRemoteAddr();
	}
}
