package com.processing_backend.document.dto;

import java.time.Instant;
import java.util.UUID;

import com.processing_backend.document.entity.DocumentStatus;

public record DocumentListItemResponse(
		UUID documentId,
		DocumentStatus status,
		String originalFilename,
		long fileSize,
		Integer pageCount,
		boolean piiDetected,
		String piiDecision,
		boolean maskingApplied,
		Integer chunkCount,
		String embeddingProvider,
		Instant createdAt,
		Instant updatedAt
) {
}
