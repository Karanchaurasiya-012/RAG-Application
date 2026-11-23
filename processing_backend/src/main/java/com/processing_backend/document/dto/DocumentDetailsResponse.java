package com.processing_backend.document.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.pii.PiiSummaryItem;

public record DocumentDetailsResponse(
		UUID documentId,
		DocumentStatus status,
		String originalFilename,
		String contentType,
		long fileSize,
		String checksumSha256,
		Integer pageCount,
		Integer chunkCount,
		boolean piiDetected,
		String piiDecision,
		boolean maskingApplied,
		List<PiiSummaryItem> piiSummary,
		List<String> availableActions,
		boolean originalPdfWillRemainUnmodified,
		boolean downloadAvailable,
		String embeddingProvider,
		String embeddingModel,
		Integer embeddingDimensions,
		Instant createdAt,
		Instant updatedAt
) {
}
