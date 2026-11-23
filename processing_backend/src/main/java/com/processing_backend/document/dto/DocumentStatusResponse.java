package com.processing_backend.document.dto;

import java.util.UUID;

import com.processing_backend.document.entity.DocumentStatus;

public record DocumentStatusResponse(
		UUID documentId,
		DocumentStatus status,
		boolean piiDetected,
		String piiDecision,
		boolean maskingApplied,
		Integer chunkCount,
		String embeddingProvider,
		boolean downloadAvailable,
		String jobStatus,
		String jobType,
		Integer jobAttempts,
		String lastErrorCode
) {
}
