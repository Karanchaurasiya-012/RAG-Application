package com.processing_backend.document.dto;

import java.util.UUID;

public record DocumentDownloadResponse(
		UUID documentId,
		String originalFilename,
		String downloadUrl,
		int expiresInSeconds
) {
}
