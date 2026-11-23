package com.processing_backend.document.repository;

import java.util.List;
import java.util.UUID;

import com.processing_backend.document.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

	List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

	long countByDocumentId(UUID documentId);

	void deleteByDocumentId(UUID documentId);
}
