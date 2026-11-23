package com.processing_backend.embedding;

import java.util.List;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates that chunk vectors are complete, correctly sized, and in one embedding space.
 */
@Component
public class VectorPersistenceValidator {

	public void requirePersisted(
			UUID documentOwnerUserId,
			String expectedProvider,
			String expectedModel,
			int expectedDimensions,
			int expectedChunkCount,
			List<ChunkEmbeddingStore.ChunkVectorRow> rows
	) {
		if (rows == null || rows.isEmpty()) {
			throw new BusinessException("No chunk vectors found for persistence verification.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
		if (expectedChunkCount > 0 && rows.size() != expectedChunkCount) {
			throw new BusinessException(
					"Chunk count mismatch during vector persistence: expected "
							+ expectedChunkCount + " but found " + rows.size() + ".",
					HttpStatus.UNPROCESSABLE_ENTITY
			);
		}
		if (expectedProvider == null || expectedProvider.isBlank()) {
			throw new BusinessException("Document embedding provider missing for vector persistence.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		for (ChunkEmbeddingStore.ChunkVectorRow row : rows) {
			if (row.dimensions() == null) {
				throw new BusinessException(
						"Chunk embedding missing for chunkId=" + row.chunkId() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			if (row.dimensions() != expectedDimensions) {
				throw new BusinessException(
						"Chunk embedding dimension mismatch for chunkId=" + row.chunkId()
								+ ": expected " + expectedDimensions + " but got " + row.dimensions() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			if (row.ownerUserId() == null || !row.ownerUserId().equals(documentOwnerUserId)) {
				throw new BusinessException(
						"Chunk owner mismatch for chunkId=" + row.chunkId() + ".",
						HttpStatus.INTERNAL_SERVER_ERROR
				);
			}
			if (row.embeddingProvider() == null
					|| !expectedProvider.equalsIgnoreCase(row.embeddingProvider())) {
				throw new BusinessException(
						"Chunk embedding provider mismatch for chunkId=" + row.chunkId() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			if (expectedModel != null && !expectedModel.isBlank()
					&& (row.embeddingModel() == null || !expectedModel.equals(row.embeddingModel()))) {
				throw new BusinessException(
						"Chunk embedding model mismatch for chunkId=" + row.chunkId() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
		}
	}
}
