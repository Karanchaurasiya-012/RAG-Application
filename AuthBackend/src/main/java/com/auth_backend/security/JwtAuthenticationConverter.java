package com.auth_backend.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}

	private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
		Object rolesClaim = jwt.getClaims().get("roles");
		List<String> roles = new ArrayList<>();

		if (rolesClaim instanceof Collection<?> collection) {
			for (Object value : collection) {
				if (value != null) {
					roles.add(value.toString());
				}
			}
		}

		return roles.stream()
				.map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toUnmodifiableSet());
	}
}
