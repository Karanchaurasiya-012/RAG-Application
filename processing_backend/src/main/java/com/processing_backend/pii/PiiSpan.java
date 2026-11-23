package com.processing_backend.pii;

/**
 * In-memory detection span. Raw matched text is kept only transiently for overlap
 * resolution and must never be persisted or logged.
 */
public record PiiSpan(
		PiiEntityType entityType,
		int pageNumber,
		int startOffset,
		int endOffset,
		double confidence
) {
	public int length() {
		return endOffset - startOffset;
	}

	public boolean overlaps(PiiSpan other) {
		if (pageNumber != other.pageNumber) {
			return false;
		}
		return startOffset < other.endOffset && other.startOffset < endOffset;
	}
}
