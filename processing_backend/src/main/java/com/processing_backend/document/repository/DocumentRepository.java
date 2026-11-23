package com.processing_backend.document.repository;

import java.util.Optional;
import java.util.UUID;

import com.processing_backend.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

	Optional<Document> findByOwnerUserIdAndChecksumSha256(UUID ownerUserId, String checksumSha256);

	Optional<Document> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

	Page<Document> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId, Pageable pageable);
}
