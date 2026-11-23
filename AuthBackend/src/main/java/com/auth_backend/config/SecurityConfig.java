package com.auth_backend.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.auth_backend.security.CookieBearerTokenResolver;
import com.auth_backend.security.CustomOAuth2FailureHandler;
import com.auth_backend.security.CustomOAuth2SuccessHandler;
import com.auth_backend.security.JwtAuthenticationConverter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final CookieBearerTokenResolver cookieBearerTokenResolver;
	private final JwtAuthenticationConverter jwtAuthenticationConverter;
	private final CustomOAuth2SuccessHandler customOAuth2SuccessHandler;
	private final CustomOAuth2FailureHandler customOAuth2FailureHandler;
	private final ObjectMapper objectMapper;

	public SecurityConfig(
			CookieBearerTokenResolver cookieBearerTokenResolver,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			CustomOAuth2SuccessHandler customOAuth2SuccessHandler,
			CustomOAuth2FailureHandler customOAuth2FailureHandler,
			ObjectMapper objectMapper
	) {
		this.cookieBearerTokenResolver = cookieBearerTokenResolver;
		this.jwtAuthenticationConverter = jwtAuthenticationConverter;
		this.customOAuth2SuccessHandler = customOAuth2SuccessHandler;
		this.customOAuth2FailureHandler = customOAuth2FailureHandler;
		this.objectMapper = objectMapper;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.spa())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
						.requestMatchers(HttpMethod.POST,
								"/api/v1/auth/register",
								"/api/v1/auth/verify-email",
								"/api/v1/auth/resend-verification",
								"/api/v1/auth/login",
								"/api/v1/auth/forgot-password",
								"/api/v1/auth/reset-password",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout"
						).permitAll()
						.requestMatchers(
								"/oauth2/authorization/**",
								"/login/oauth2/code/**"
						).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
						.anyRequest().authenticated()
				)
				.oauth2ResourceServer(oauth2 -> oauth2
						.bearerTokenResolver(cookieBearerTokenResolver)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
						.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler())
				)
				.oauth2Login(oauth2 -> oauth2
						.successHandler(customOAuth2SuccessHandler)
						.failureHandler(customOAuth2FailureHandler)
				)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(authenticationEntryPoint())
						.accessDeniedHandler(accessDeniedHandler())
				);

		return http.build();
	}

	@Bean
	AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) -> writeJsonError(
				response,
				HttpServletResponse.SC_UNAUTHORIZED,
				"Authentication required."
		);
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			if (isCsrfFailure(accessDeniedException)) {
				writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "CSRF token missing or invalid.");
				return;
			}
			writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, "Access denied.");
		};
	}

	private boolean isCsrfFailure(AccessDeniedException exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof MissingCsrfTokenException || current instanceof InvalidCsrfTokenException) {
				return true;
			}
			current = current.getCause();
		}
		String message = exception.getMessage();
		return message != null && message.toLowerCase().contains("csrf");
	}

	private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("success", false);
		body.put("message", message);
		body.put("data", null);
		objectMapper.writeValue(response.getOutputStream(), body);
	}
}
