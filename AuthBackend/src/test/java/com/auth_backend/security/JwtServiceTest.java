package com.auth_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

	@Autowired
	private JwtService jwtService;

	@Test
	void createsAndDecodesAccessTokenWithRoles() {
		UUID userId = UUID.randomUUID();
		String token = jwtService.createAccessToken(userId, List.of("USER"));

		Jwt jwt = jwtService.decode(token);

		assertThat(jwt.getSubject()).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
		assertThat(jwt.getHeaders().get("kid")).isEqualTo("auth-key-1");
		assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
		assertThat(jwt.getId()).isNotBlank();
	}

	@Test
	void rejectsTamperedToken() {
		UUID userId = UUID.randomUUID();
		String token = jwtService.createAccessToken(userId, List.of("USER"));
		String tampered = token.substring(0, token.length() - 4) + "xxxx";

		assertThatThrownBy(() -> jwtService.decode(tampered))
				.isInstanceOf(BadJwtException.class);
	}
}
