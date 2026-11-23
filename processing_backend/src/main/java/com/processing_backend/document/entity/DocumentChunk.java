package com.processing_backend.document.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Persisted text chunk. The VECTOR embedding column is written via {@code ChunkEmbeddingStore}
 * (JDBC) so Hibernate does not map pgvector types.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

	@Id
	private UUID id;

	@Column(name = "document_id", nullable = false)
	private UUID documentId;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "page_start")
	private Integer pageStart;

	@Column(name = "page_end")
	private Integer pageEnd;

	@Column(name = "token_count")
	private Integer tokenCount;

	@Column(name = "embedding_provider", length = 50)
	private String embeddingProvider;

	@Column(name = "embedding_model", length = 150)
	private String embeddingModel;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getDocumentId() {
		return documentId;
	}

	public void setDocumentId(UUID documentId) {
		this.documentId = documentId;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public int getChunkIndex() {
		return chunkIndex;
	}

	public void setChunkIndex(int chunkIndex) {
		this.chunkIndex = chunkIndex;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getPageStart() {
		return pageStart;
	}

	public void setPageStart(Integer pageStart) {
		this.pageStart = pageStart;
	}

	public Integer getPageEnd() {
		return pageEnd;
	}

	public void setPageEnd(Integer pageEnd) {
		this.pageEnd = pageEnd;
	}

	public Integer getTokenCount() {
		return tokenCount;
	}

	public void setTokenCount(Integer tokenCount) {
		this.tokenCount = tokenCount;
	}

	public String getEmbeddingProvider() {
		return embeddingProvider;
	}

	public void setEmbeddingProvider(String embeddingProvider) {
		this.embeddingProvider = embeddingProvider;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public void setEmbeddingModel(String embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
