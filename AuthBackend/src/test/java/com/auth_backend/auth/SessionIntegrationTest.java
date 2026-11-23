package com.auth_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.auth_backend.auth.entity.RefreshToken;
import com.auth_backend.auth.entity.RoleName;
import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.RefreshTokenRepository;
import com.auth_backend.auth.repository.RoleRepository;
import com.auth_backend.auth.repository.UserRepository;
import com.auth_backend.auth.service.SessionService;
import com.auth_backend.auth.util.TokenHasher;
import com.auth_backend.security.AuthCookieNames;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void clean() {
		transactionTemplate.executeWithoutResult(status -> {
			refreshTokenRepository.deleteAll();
			userRepository.deleteAll();
		});
	}

	@Test
	void refreshRotatesTokensAndKeepsFamily() throws Exception {
		persistActiveUser("refresh-ok@example.com", "StrongPassword123!");
		Cookie refreshCookie = loginAndGetRefreshCookie("refresh-ok@example.com", "StrongPassword123!");
		String oldHash = TokenHasher.sha256(refreshCookie.getValue());

		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(refreshCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SessionService.REFRESH_SUCCESS_MESSAGE))
				.andReturn();

		Cookie newRefresh = refreshResult.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
		Cookie newAccess = refreshResult.getResponse().getCookie(AuthCookieNames.ACCESS_TOKEN);
		assertThat(newRefresh).isNotNull();
		assertThat(newAccess).isNotNull();
		assertThat(newRefresh.getValue()).isNotEqualTo(refreshCookie.getValue());

		transactionTemplate.executeWithoutResult(status -> {
			RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash).orElseThrow();
			assertThat(oldToken.isRevoked()).isTrue();
			assertThat(oldToken.getReplacedBy()).isNotNull();

			RefreshToken newToken = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(newRefresh.getValue())).orElseThrow();
			assertThat(newToken.getFamilyId()).isEqualTo(oldToken.getFamilyId());
			assertThat(newToken.isRevoked()).isFalse();
			assertThat(oldToken.getReplacedBy().getId()).isEqualTo(newToken.getId());
		});
	}

	@Test
	void refreshTokenReuseRevokesFamily() throws Exception {
		persistActiveUser("reuse@example.com", "StrongPassword123!");
		Cookie originalRefresh = loginAndGetRefreshCookie("reuse@example.com", "StrongPassword123!");

		MvcResult firstRefresh = mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(originalRefresh))
				.andExpect(status().isOk())
				.andReturn();
		Cookie rotated = firstRefresh.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
		assertThat(rotated).isNotNull();

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(originalRefresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(SessionService.INVALID_REFRESH_MESSAGE));

		RefreshToken original = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(originalRefresh.getValue())).orElseThrow();
		List<RefreshToken> family = refreshTokenRepository.findByFamilyId(original.getFamilyId());
		assertThat(family).isNotEmpty();
		assertThat(family).allMatch(RefreshToken::isRevoked);
	}

	@Test
	void logoutRevokesRefreshAndClearsCookies() throws Exception {
		persistActiveUser("logout@example.com", "StrongPassword123!");
		Cookie refreshCookie = loginAndGetRefreshCookie("logout@example.com", "StrongPassword123!");
		String hash = TokenHasher.sha256(refreshCookie.getValue());

		MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
						.with(csrf())
						.cookie(refreshCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SessionService.LOGOUT_SUCCESS_MESSAGE))
				.andReturn();

		assertThat(refreshTokenRepository.findByTokenHash(hash).orElseThrow().isRevoked()).isTrue();

		String setCookie = String.join(";", logoutResult.getResponse().getHeaders("Set-Cookie"));
		assertThat(setCookie).contains(AuthCookieNames.ACCESS_TOKEN);
		assertThat(setCookie).contains(AuthCookieNames.REFRESH_TOKEN);
		assertThat(setCookie).contains("Max-Age=0");

		mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(refreshCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshWithoutCookieIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(SessionService.INVALID_REFRESH_MESSAGE));
	}

	@Test
	void concurrentRefreshAllowsOnlyOneWinner() throws Exception {
		persistActiveUser("concurrent@example.com", "StrongPassword123!");
		Cookie refreshCookie = loginAndGetRefreshCookie("concurrent@example.com", "StrongPassword123!");

		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<Integer> task = () -> mockMvc.perform(post("/api/v1/auth/refresh")
						.with(csrf())
						.cookie(refreshCookie))
				.andReturn()
				.getResponse()
				.getStatus();

		try {
			Future<Integer> first = executor.submit(task);
			Future<Integer> second = executor.submit(task);
			int statusA = first.get(15, TimeUnit.SECONDS);
			int statusB = second.get(15, TimeUnit.SECONDS);

			assertThat(List.of(statusA, statusB)).contains(200);
			assertThat(List.of(statusA, statusB)).contains(401);
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Cookie loginAndGetRefreshCookie(String email, String password) throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "email":"%s",
								  "password":"%s"
								}
								""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn();

		Cookie refresh = loginResult.getResponse().getCookie(AuthCookieNames.REFRESH_TOKEN);
		assertThat(refresh).isNotNull();
		return refresh;
	}

	private User persistActiveUser(String email, String rawPassword) {
		return transactionTemplate.execute(status -> {
			User user = new User();
			user.setFullName("Session User");
			user.setEmail(email);
			user.setPasswordHash(passwordEncoder.encode(rawPassword));
			user.setStatus(UserStatus.ACTIVE);
			user.setEmailVerifiedAt(Instant.now());
			user.addRole(roleRepository.findByName(RoleName.USER).orElseThrow());
			return userRepository.save(user);
		});
	}
}
