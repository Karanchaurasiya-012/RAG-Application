package com.auth_backend.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.AuthIdentity;
import com.auth_backend.auth.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

	Optional<AuthIdentity> findByProviderAndProviderSubject(AuthProvider provider, String providerSubject);

	List<AuthIdentity> findByUserId(UUID userId);

	boolean existsByProviderAndProviderSubject(AuthProvider provider, String providerSubject);
}
