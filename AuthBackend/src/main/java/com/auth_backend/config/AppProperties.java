package com.auth_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private String frontendUrl;
	private final Email email = new Email();
	private final Jwt jwt = new Jwt();

	public String getFrontendUrl() {
		return frontendUrl;
	}

	public void setFrontendUrl(String frontendUrl) {
		this.frontendUrl = frontendUrl;
	}

	public Email getEmail() {
		return email;
	}

	public Jwt getJwt() {
		return jwt;
	}

	public static class Email {
		private String provider = "smtp";
		private String from;
		private String verificationTokenTtl = "24h";
		private String passwordResetTokenTtl = "1h";

		public String getProvider() {
			return provider;
		}

		public void setProvider(String provider) {
			this.provider = provider;
		}

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getVerificationTokenTtl() {
			return verificationTokenTtl;
		}

		public void setVerificationTokenTtl(String verificationTokenTtl) {
			this.verificationTokenTtl = verificationTokenTtl;
		}

		public String getPasswordResetTokenTtl() {
			return passwordResetTokenTtl;
		}

		public void setPasswordResetTokenTtl(String passwordResetTokenTtl) {
			this.passwordResetTokenTtl = passwordResetTokenTtl;
		}
	}

	public static class Jwt {
		private String issuer;
		private String audience;
		private String privateKeyPath;
		private String publicKeyPath;
		private String accessTokenTtl = "10m";
		private String refreshTokenTtl = "7d";

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}

		public String getAudience() {
			return audience;
		}

		public void setAudience(String audience) {
			this.audience = audience;
		}

		public String getPrivateKeyPath() {
			return privateKeyPath;
		}

		public void setPrivateKeyPath(String privateKeyPath) {
			this.privateKeyPath = privateKeyPath;
		}

		public String getPublicKeyPath() {
			return publicKeyPath;
		}

		public void setPublicKeyPath(String publicKeyPath) {
			this.publicKeyPath = publicKeyPath;
		}

		public String getAccessTokenTtl() {
			return accessTokenTtl;
		}

		public void setAccessTokenTtl(String accessTokenTtl) {
			this.accessTokenTtl = accessTokenTtl;
		}

		public String getRefreshTokenTtl() {
			return refreshTokenTtl;
		}

		public void setRefreshTokenTtl(String refreshTokenTtl) {
			this.refreshTokenTtl = refreshTokenTtl;
		}
	}
}
