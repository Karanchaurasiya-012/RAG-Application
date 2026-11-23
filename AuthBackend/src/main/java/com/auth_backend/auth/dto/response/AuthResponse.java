package com.auth_backend.auth.dto.response;

public class AuthResponse {

	private UserResponse user;
	private String accessToken;

	public AuthResponse() {
	}

	public AuthResponse(UserResponse user) {
		this.user = user;
	}

	public AuthResponse(UserResponse user, String accessToken) {
		this.user = user;
		this.accessToken = accessToken;
	}

	public static AuthResponse accessTokenOnly(String accessToken) {
		AuthResponse response = new AuthResponse();
		response.accessToken = accessToken;
		return response;
	}

	public UserResponse getUser() {
		return user;
	}

	public void setUser(UserResponse user) {
		this.user = user;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
}
