package com.processing_backend.embedding;

/**
 * Provider-independent embedding client. Implementations must not silently fall back
 * to another provider.
 */
public interface EmbeddingProvider {

	EmbeddingProviderId id();

	String model();

	/**
	 * Embed text for document indexing ({@link EmbeddingTaskType#RETRIEVAL_DOCUMENT}).
	 */
	default EmbeddingVector embed(String text) {
		return embed(text, EmbeddingTaskType.RETRIEVAL_DOCUMENT);
	}

	/**
	 * Embed text with an explicit task type (required for Gemini retrieval quality).
	 */
	EmbeddingVector embed(String text, EmbeddingTaskType taskType);
}
