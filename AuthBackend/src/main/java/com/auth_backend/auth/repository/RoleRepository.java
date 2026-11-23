package com.auth_backend.auth.repository;

import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.Role;
import com.auth_backend.auth.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByName(RoleName name);

	boolean existsByName(RoleName name);
}
