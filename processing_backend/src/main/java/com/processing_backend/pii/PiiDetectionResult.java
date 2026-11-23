package com.processing_backend.pii;

import java.util.List;

public record PiiDetectionResult(
		boolean hasPii,
		List<PiiSpan> spans,
		List<PiiSummaryItem> summary
) {
}
