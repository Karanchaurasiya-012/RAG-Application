package com.auth_backend.auth.mapper;

import java.util.List;

import com.auth_backend.auth.dto.response.UserResponse;
import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		UserResponse response = new UserResponse();
		response.setId(user.getId());
		response.setFullName(user.getFullName());
		response.setEmail(user.getEmail());
		response.setEmailVerified(user.getEmailVerifiedAt() != null);
		response.setRoles(user.getRoles().stream()
				.map(Role::getName)
				.map(Enum::name)
				.sorted()
				.toList());
		return response;
	}

	public List<String> roleNames(User user) {
		return user.getRoles().stream()
				.map(Role::getName)
				.map(Enum::name)
				.sorted()
				.toList();
	}
}
