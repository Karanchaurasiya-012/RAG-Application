package com.auth_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

	private final PasswordEncoder passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

	@Test
	void encodesWithArgon2idAndMatches() {
		String raw = "StrongPassword123!";
		String encoded = passwordEncoder.encode(raw);

		assertThat(encoded).isNotEqualTo(raw);
		assertThat(encoded.toLowerCase()).contains("argon2");
		assertThat(passwordEncoder.matches(raw, encoded)).isTrue();
		assertThat(passwordEncoder.matches("wrong-password", encoded)).isFalse();
	}
}
