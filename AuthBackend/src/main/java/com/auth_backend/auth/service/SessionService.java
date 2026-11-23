package com.auth_backend.auth.service;

import java.time.Instant;
import java.util.UUID;

import com.auth_backend.auth.entity.RefreshToken;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.RefreshTokenRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.util.SecureTokenGenerator;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.common.exception.BusinessException;
import com.auth_backend.security.AuthCookieService;
import com.auth_backend.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SessionService {

	public static final String REFRESH_SUCCESS_MESSAGE = "Session refreshed successfully.";
	public static final String LOGOUT_SUCCESS_MESSAGE = "Logged out successfully.";
	public static final String INVALID_REFRESH_MESSAGE = "Invalid or expired refresh session.";

	private static final Logger log = LoggerFactory.getLogger(SessionService.class);

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final AuthCookieService authCookieService;

	public SessionService(
			RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository,
			JwtService jwtService,
			AuthCookieService authCookieService
	) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.authCookieService = authCookieService;
	}

	public record IssuedSession(String accessToken, String refreshToken) {
	}

	@Transactional
	public IssuedSession issueSession(User user, String userAgent, String ipAddress) {
		String accessToken = createAccessToken(user);
		String rawRefreshToken = SecureTokenGenerator.generate(48);

		RefreshToken refreshToken = new RefreshToken();
		refreshToken.setUser(user);
		refreshToken.setTokenHash(TokenHasher.sha256(rawRefreshToken));
		refreshToken.setFamilyId(UUID.randomUUID());
		refreshToken.setExpiresAt(Instant.now().plus(jwtService.refreshTokenTtl()));
		refreshToken.setUserAgent(userAgent);
		refreshToken.setIpAddress(normalizeIp(ipAddress));
		refreshTokenRepository.save(refreshToken);

		return new IssuedSession(accessToken, rawRefreshToken);
	}

	/**
	 * BusinessException must not roll back security side-effects such as
	 * refresh-family revocation after reuse detection.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public void refresh(HttpServletRequest request, HttpServletResponse response) {
		String rawRefreshToken = authCookieService.readRefreshToken(request);
		if (!StringUtils.hasText(rawRefreshToken)) {
			throw invalidRefresh();
		}

		Instant now = Instant.now();
		String tokenHash = TokenHasher.sha256(rawRefreshToken);
		RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(this::invalidRefresh);

		if (current.isRevoked()) {
			log.warn("Refresh token reuse detected for familyId={}", current.getFamilyId());
			refreshTokenRepository.revokeAllActiveForFamily(current.getFamilyId(), now);
			authCookieService.clearAuthCookies(response);
			throw invalidRefresh();
		}

		if (current.isExpired(now)) {
			current.setRevokedAt(now);
			refreshTokenRepository.save(current);
			authCookieService.clearAuthCookies(response);
			throw invalidRefresh();
		}

		User user = userRepository.findWithRolesById(current.getUser().getId())
				.orElseThrow(this::invalidRefresh);

		if (user.getStatus() != UserStatus.ACTIVE) {
			refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
			authCookieService.clearAuthCookies(response);
			throw invalidRefresh();
		}

		String newRawRefreshToken = SecureTokenGenerator.generate(48);
		RefreshToken replacement = new RefreshToken();
		replacement.setUser(user);
		replacement.setTokenHash(TokenHasher.sha256(newRawRefreshToken));
		replacement.setFamilyId(current.getFamilyId());
		replacement.setExpiresAt(now.plus(jwtService.refreshTokenTtl()));
		replacement.setUserAgent(request.getHeader("User-Agent"));
		replacement.setIpAddress(normalizeIp(resolveClientIp(request)));
		replacement = refreshTokenRepository.save(replacement);

		current.setRevokedAt(now);
		current.setLastUsedAt(now);
		current.setReplacedBy(replacement);
		refreshTokenRepository.save(current);

		String accessToken = createAccessToken(user);
		authCookieService.setAccessTokenCookie(response, accessToken);
		authCookieService.setRefreshTokenCookie(response, newRawRefreshToken);
	}

	@Transactional
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		String rawRefreshToken = authCookieService.readRefreshToken(request);
		if (StringUtils.hasText(rawRefreshToken)) {
			refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawRefreshToken)).ifPresent(token -> {
				if (!token.isRevoked()) {
					token.setRevokedAt(Instant.now());
					refreshTokenRepository.save(token);
				}
			});
		}
		authCookieService.clearAuthCookies(response);
	}

	private String createAccessToken(User user) {
		return jwtService.createAccessToken(
				user.getId(),
				user.getRoles().stream().map(role -> role.getName().name()).sorted().toList()
		);
	}

	private BusinessException invalidRefresh() {
		return new BusinessException(INVALID_REFRESH_MESSAGE, HttpStatus.UNAUTHORIZED);
	}

	private String resolveClientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			return forwarded;
		}
		return request.getRemoteAddr();
	}

	private String normalizeIp(String ipAddress) {
		if (ipAddress == null || ipAddress.isBlank()) {
			return null;
		}
		String first = ipAddress.split(",")[0].trim();
		return first.isEmpty() ? null : first;
	}
}
