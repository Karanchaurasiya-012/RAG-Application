package com.auth_backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email);

	Optional<User> findByEmail(String email);

	@EntityGraph(attributePaths = "roles")
	Optional<User> findWithRolesById(UUID id);

	@EntityGraph(attributePaths = "roles")
	Optional<User> findWithRolesByEmail(String email);
}
