package com.processing_backend.pii;

import java.util.List;

public record PiiSummaryItem(
		PiiEntityType type,
		int count,
		List<Integer> pages
) {
}
