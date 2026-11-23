package com.auth_backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

	Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

	List<EmailVerificationToken> findByUserId(UUID userId);
}
