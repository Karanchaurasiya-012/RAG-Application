package com.processing_backend.chunking;

/**
 * One chunk produced from final extracted/masked page text (pre-embedding).
 */
public record TextChunk(
		int chunkIndex,
		String content,
		int pageStart,
		int pageEnd,
		int tokenCount
) {
}
