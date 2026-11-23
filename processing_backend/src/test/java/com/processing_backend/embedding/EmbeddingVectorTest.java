package com.processing_backend.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbeddingVectorTest {

	@Test
	void buildsPgVectorLiteral() {
		EmbeddingVector vector = new EmbeddingVector(new float[]{0.5f, -1.25f, 0f}, "ollama", "bge-m3", 3);
		assertThat(vector.toPgVectorLiteral()).isEqualTo("[0.5,-1.25,0.0]");
	}
}
