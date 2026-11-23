package com.auth_backend.security;

import java.util.stream.Collectors;

import com.auth_backend.auth.entity.User;
import com.auth_backend.auth.entity.UserStatus;
import com.auth_backend.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		String email = username == null ? "" : username.trim().toLowerCase();
		User user = userRepository.findWithRolesByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));

		boolean enabled = user.getStatus() == UserStatus.ACTIVE;
		boolean accountNonLocked = user.getStatus() != UserStatus.BLOCKED;
		String password = user.getPasswordHash() == null ? "" : user.getPasswordHash();

		return org.springframework.security.core.userdetails.User.builder()
				.username(user.getEmail())
				.password(password)
				.authorities(user.getRoles().stream()
						.map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
						.collect(Collectors.toSet()))
				.disabled(!enabled)
				.accountLocked(!accountNonLocked)
				.build();
	}
}
