package com.processing_backend.document.dto;

import java.util.UUID;

import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiDecision;

public record PiiDecisionResponse(
		UUID documentId,
		DocumentStatus status,
		PiiDecision decision,
		boolean originalPdfWillRemainUnmodified,
		String nextStep
) {
}
