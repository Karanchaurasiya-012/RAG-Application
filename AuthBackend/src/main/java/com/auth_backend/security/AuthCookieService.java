package com.auth_backend.security;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthCookieService {

	private final JwtService jwtService;

	public AuthCookieService(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	public void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
		Duration ttl = jwtService.accessTokenTtl();
		ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.ACCESS_TOKEN, accessToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(ttl)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
		Duration ttl = jwtService.refreshTokenTtl();
		ResponseCookie cookie = ResponseCookie.from(AuthCookieNames.REFRESH_TOKEN, refreshToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(AuthCookieNames.REFRESH_COOKIE_PATH)
				.maxAge(ttl)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearAuthCookies(HttpServletResponse response) {
		ResponseCookie access = ResponseCookie.from(AuthCookieNames.ACCESS_TOKEN, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
		ResponseCookie refresh = ResponseCookie.from(AuthCookieNames.REFRESH_TOKEN, "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(AuthCookieNames.REFRESH_COOKIE_PATH)
				.maxAge(0)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
	}

	public String readRefreshToken(HttpServletRequest request) {
		return readCookie(request, AuthCookieNames.REFRESH_TOKEN);
	}

	public String readAccessToken(HttpServletRequest request) {
		return readCookie(request, AuthCookieNames.ACCESS_TOKEN);
	}

	private String readCookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
