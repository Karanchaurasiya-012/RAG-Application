package com.auth_backend.security;

import java.io.IOException;

import com.auth_backend.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(CustomOAuth2FailureHandler.class);

	private final AppProperties appProperties;

	public CustomOAuth2FailureHandler(AppProperties appProperties) {
		this.appProperties = appProperties;
	}

	@Override
	public void onAuthenticationFailure(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException exception
	) throws IOException {
		log.warn("Google OAuth authentication failed: {}", exception.getMessage());
		SecurityContextHolder.clearContext();

		String redirect = UriComponentsBuilder
				.fromUriString(appProperties.getFrontendUrl())
				.path("/login")
				.queryParam("error", "oauth_failed")
				.build()
				.encode()
				.toUriString();
		response.sendRedirect(redirect);
	}
}
