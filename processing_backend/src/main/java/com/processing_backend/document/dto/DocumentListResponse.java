package com.processing_backend.document.dto;

import java.util.List;

public record DocumentListResponse(
		List<DocumentListItemResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages
) {
}
