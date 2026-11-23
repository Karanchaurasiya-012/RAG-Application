package com.processing_backend.document.dto;

import java.util.UUID;

public record DocumentDeleteResponse(
		UUID documentId,
		boolean deleted,
		boolean s3ObjectDeleted,
		boolean localTempDeleted
) {
}
