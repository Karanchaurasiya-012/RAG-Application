package com.auth_backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	List<PasswordResetToken> findByUserId(UUID userId);
}
