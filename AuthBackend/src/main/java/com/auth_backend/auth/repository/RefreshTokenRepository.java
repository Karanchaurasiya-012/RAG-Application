package com.auth_backend.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.auth_backend.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	List<RefreshToken> findByFamilyId(UUID familyId);

	List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken rt
			   set rt.revokedAt = :revokedAt
			 where rt.user.id = :userId
			   and rt.revokedAt is null
			""")
	int revokeAllActiveForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update RefreshToken rt
			   set rt.revokedAt = :revokedAt
			 where rt.familyId = :familyId
			   and rt.revokedAt is null
			""")
	int revokeAllActiveForFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}
