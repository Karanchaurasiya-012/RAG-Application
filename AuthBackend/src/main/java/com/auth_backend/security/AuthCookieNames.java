package com.auth_backend.security;

public final class AuthCookieNames {

	public static final String ACCESS_TOKEN = "__Host-access_token";
	public static final String REFRESH_TOKEN = "__Secure-refresh_token";
	public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

	private AuthCookieNames() {
	}
}
