package com.processing_backend.chunking;

/**
 * One estimated token with its source page and character offsets in that page's text.
 */
public record TokenSpan(
		int pageNumber,
		int startInclusive,
		int endExclusive,
		String text
) {
	public TokenSpan {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("pageNumber must be >= 1");
		}
		if (startInclusive < 0 || endExclusive < startInclusive) {
			throw new IllegalArgumentException("invalid token offsets");
		}
		if (text == null || text.isEmpty()) {
			throw new IllegalArgumentException("token text must be non-empty");
		}
	}
}
