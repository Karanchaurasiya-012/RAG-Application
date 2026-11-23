package com.processing_backend.embedding;

/**
 * Gemini retrieval task types. Ollama ignores this hint.
 */
public enum EmbeddingTaskType {
	RETRIEVAL_DOCUMENT,
	RETRIEVAL_QUERY
}
