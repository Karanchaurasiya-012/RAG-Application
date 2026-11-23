package com.processing_backend.embedding;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class VectorPersistenceValidatorTest {

	private final VectorPersistenceValidator validator = new VectorPersistenceValidator();
	private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private final UUID chunkId = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Test
	void acceptsConsistentRows() {
		assertThatCode(() -> validator.requirePersisted(
				owner,
				"ollama",
				"bge-m3:latest",
				1024,
				1,
				List.of(new ChunkEmbeddingStore.ChunkVectorRow(chunkId, owner, "ollama", "bge-m3:latest", 1024))
		)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMissingEmbedding() {
		assertThatThrownBy(() -> validator.requirePersisted(
				owner,
				"ollama",
				"bge-m3:latest",
				1024,
				1,
				List.of(new ChunkEmbeddingStore.ChunkVectorRow(chunkId, owner, "ollama", "bge-m3:latest", null))
		)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("missing");
	}

	@Test
	void rejectsProviderMismatch() {
		assertThatThrownBy(() -> validator.requirePersisted(
				owner,
				"ollama",
				"bge-m3:latest",
				1024,
				1,
				List.of(new ChunkEmbeddingStore.ChunkVectorRow(chunkId, owner, "gemini", "gemini-embedding-001", 1024))
		)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("provider mismatch");
	}

	@Test
	void rejectsOwnerMismatch() {
		assertThatThrownBy(() -> validator.requirePersisted(
				owner,
				"ollama",
				"bge-m3:latest",
				1024,
				1,
				List.of(new ChunkEmbeddingStore.ChunkVectorRow(
						chunkId, UUID.randomUUID(), "ollama", "bge-m3:latest", 1024))
		)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("owner mismatch");
	}

	@Test
	void rejectsDimensionMismatch() {
		assertThatThrownBy(() -> validator.requirePersisted(
				owner,
				"ollama",
				"bge-m3:latest",
				1024,
				1,
				List.of(new ChunkEmbeddingStore.ChunkVectorRow(chunkId, owner, "ollama", "bge-m3:latest", 768))
		)).isInstanceOf(BusinessException.class)
				.hasMessageContaining("dimension mismatch");
	}
}
