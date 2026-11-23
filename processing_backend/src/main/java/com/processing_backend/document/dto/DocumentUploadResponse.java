package com.processing_backend.document.dto;

import java.util.List;
import java.util.UUID;

import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.pii.PiiSummaryItem;

public record DocumentUploadResponse(
		UUID documentId,
		DocumentStatus status,
		String originalFilename,
		long fileSize,
		int pageCount,
		int pagesExtracted,
		int nonEmptyPages,
		String checksumSha256,
		boolean duplicate,
		boolean piiDetected,
		List<PiiSummaryItem> piiSummary,
		List<String> availableActions,
		Boolean originalPdfWillRemainUnmodified,
		Boolean processingStarted
) {
}
