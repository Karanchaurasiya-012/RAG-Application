package com.processing_backend.embedding;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access for VECTOR(1024) columns and similarity probes.
 * Hibernate intentionally does not map the embedding column.
 */
@Repository
public class ChunkEmbeddingStore {

	private final JdbcTemplate jdbcTemplate;

	public ChunkEmbeddingStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void saveEmbedding(UUID chunkId, EmbeddingVector vector) {
		jdbcTemplate.update(
				"""
						UPDATE document_chunks
						SET embedding = CAST(? AS vector),
						    embedding_provider = ?,
						    embedding_model = ?
						WHERE id = ?
						""",
				vector.toPgVectorLiteral(),
				vector.provider(),
				vector.model(),
				chunkId
		);
	}

	public Integer readDimensions(UUID chunkId) {
		return jdbcTemplate.query(
				"""
						SELECT CASE WHEN embedding IS NULL THEN NULL ELSE vector_dims(embedding) END AS dims
						FROM document_chunks
						WHERE id = ?
						""",
				rs -> rs.next() ? (Integer) rs.getObject("dims") : null,
				chunkId
		);
	}

	public int countChunks(UUID documentId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM document_chunks WHERE document_id = ?",
				Integer.class,
				documentId
		);
		return count == null ? 0 : count;
	}

	public int countEmbeddedChunks(UUID documentId) {
		Integer count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*) FROM document_chunks
						WHERE document_id = ?
						  AND embedding IS NOT NULL
						""",
				Integer.class,
				documentId
		);
		return count == null ? 0 : count;
	}

	public List<ChunkVectorRow> loadVectorMetadata(UUID documentId) {
		return jdbcTemplate.query(
				"""
						SELECT id,
						       owner_user_id,
						       embedding_provider,
						       embedding_model,
						       CASE WHEN embedding IS NULL THEN NULL ELSE vector_dims(embedding) END AS dims
						FROM document_chunks
						WHERE document_id = ?
						ORDER BY chunk_index ASC
						""",
				(rs, rowNum) -> new ChunkVectorRow(
						(UUID) rs.getObject("id"),
						(UUID) rs.getObject("owner_user_id"),
						rs.getString("embedding_provider"),
						rs.getString("embedding_model"),
						(Integer) rs.getObject("dims")
				),
				documentId
		);
	}

	public String loadFirstVectorLiteral(UUID documentId) {
		return jdbcTemplate.query(
				"""
						SELECT embedding::text AS lit
						FROM document_chunks
						WHERE document_id = ?
						  AND embedding IS NOT NULL
						ORDER BY chunk_index ASC
						LIMIT 1
						""",
				rs -> rs.next() ? rs.getString("lit") : null,
				documentId
		);
	}

	/**
	 * Owner-scoped cosine nearest-neighbor probe within one provider space.
	 */
	public List<SimilarChunkHit> findSimilar(
			UUID ownerUserId,
			String provider,
			String queryVectorLiteral,
			int limit
	) {
		return jdbcTemplate.query(
				"""
						SELECT id,
						       document_id,
						       chunk_index,
						       embedding <=> CAST(? AS vector) AS distance
						FROM document_chunks
						WHERE owner_user_id = ?
						  AND embedding_provider = ?
						  AND embedding IS NOT NULL
						ORDER BY embedding <=> CAST(? AS vector)
						LIMIT ?
						""",
				(rs, rowNum) -> new SimilarChunkHit(
						(UUID) rs.getObject("id"),
						(UUID) rs.getObject("document_id"),
						rs.getInt("chunk_index"),
						rs.getDouble("distance")
				),
				queryVectorLiteral,
				ownerUserId,
				provider,
				queryVectorLiteral,
				limit
		);
	}

	public record ChunkVectorRow(
			UUID chunkId,
			UUID ownerUserId,
			String embeddingProvider,
			String embeddingModel,
			Integer dimensions
	) {
	}

	public record SimilarChunkHit(
			UUID chunkId,
			UUID documentId,
			int chunkIndex,
			double cosineDistance
	) {
	}
}
