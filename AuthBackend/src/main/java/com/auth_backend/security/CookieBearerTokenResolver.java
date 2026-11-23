package com.auth_backend.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Prefers the HttpOnly access-token cookie, then falls back to the Authorization header.
 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

	private final DefaultBearerTokenResolver authorizationHeaderResolver = new DefaultBearerTokenResolver();

	@Override
	public String resolve(HttpServletRequest request) {
		String cookieToken = resolveFromCookie(request);
		if (StringUtils.hasText(cookieToken)) {
			return cookieToken;
		}
		return authorizationHeaderResolver.resolve(request);
	}

	private String resolveFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (AuthCookieNames.ACCESS_TOKEN.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
