package com.auth_backend.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.auth_backend.config.AppProperties;
import com.auth_backend.config.JwtConfig;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final AppProperties appProperties;

	public JwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, AppProperties appProperties) {
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.appProperties = appProperties;
	}

	public String createAccessToken(UUID userId, Collection<String> roles) {
		Instant now = Instant.now();
		Duration ttl = DurationStyle.detectAndParse(appProperties.getJwt().getAccessTokenTtl());
		Instant expiresAt = now.plus(ttl);

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
				.keyId(JwtConfig.KEY_ID)
				.build();

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(appProperties.getJwt().getIssuer())
				.audience(List.of(appProperties.getJwt().getAudience()))
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(userId.toString())
				.id(UUID.randomUUID().toString())
				.claim("roles", List.copyOf(roles))
				.build();

		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public Jwt decode(String token) {
		return jwtDecoder.decode(token);
	}

	public Duration accessTokenTtl() {
		return DurationStyle.detectAndParse(appProperties.getJwt().getAccessTokenTtl());
	}

	public Duration refreshTokenTtl() {
		return DurationStyle.detectAndParse(appProperties.getJwt().getRefreshTokenTtl());
	}
}
