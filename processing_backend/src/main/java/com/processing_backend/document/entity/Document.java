package com.processing_backend.document.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

	@Id
	private UUID id;

	@Column(name = "owner_user_id", nullable = false)
	private UUID ownerUserId;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "checksum_sha256", nullable = false, length = 64)
	private String checksumSha256;

	@Column(name = "s3_bucket", length = 255)
	private String s3Bucket;

	@Column(name = "s3_key", length = 1024)
	private String s3Key;

	@Column(name = "local_temp_path", length = 1024)
	private String localTempPath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private DocumentStatus status;

	@Column(name = "pii_detected", nullable = false)
	private boolean piiDetected;

	@Column(name = "pii_decision", length = 20)
	private String piiDecision;

	@Column(name = "masking_applied", nullable = false)
	private boolean maskingApplied;

	@Column(name = "page_count")
	private Integer pageCount;

	@Column(name = "chunk_count")
	private Integer chunkCount;

	@Column(name = "embedding_provider", length = 50)
	private String embeddingProvider;

	@Column(name = "embedding_model", length = 150)
	private String embeddingModel;

	@Column(name = "embedding_dimensions")
	private Integer embeddingDimensions;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
		if (id == null) {
			id = UUID.randomUUID();
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getOwnerUserId() {
		return ownerUserId;
	}

	public void setOwnerUserId(UUID ownerUserId) {
		this.ownerUserId = ownerUserId;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public void setOriginalFilename(String originalFilename) {
		this.originalFilename = originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public long getFileSize() {
		return fileSize;
	}

	public void setFileSize(long fileSize) {
		this.fileSize = fileSize;
	}

	public String getChecksumSha256() {
		return checksumSha256;
	}

	public void setChecksumSha256(String checksumSha256) {
		this.checksumSha256 = checksumSha256;
	}

	public String getS3Bucket() {
		return s3Bucket;
	}

	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	public String getS3Key() {
		return s3Key;
	}

	public void setS3Key(String s3Key) {
		this.s3Key = s3Key;
	}

	public String getLocalTempPath() {
		return localTempPath;
	}

	public void setLocalTempPath(String localTempPath) {
		this.localTempPath = localTempPath;
	}

	public DocumentStatus getStatus() {
		return status;
	}

	public void setStatus(DocumentStatus status) {
		this.status = status;
	}

	public boolean isPiiDetected() {
		return piiDetected;
	}

	public void setPiiDetected(boolean piiDetected) {
		this.piiDetected = piiDetected;
	}

	public String getPiiDecision() {
		return piiDecision;
	}

	public void setPiiDecision(String piiDecision) {
		this.piiDecision = piiDecision;
	}

	public boolean isMaskingApplied() {
		return maskingApplied;
	}

	public void setMaskingApplied(boolean maskingApplied) {
		this.maskingApplied = maskingApplied;
	}

	public Integer getPageCount() {
		return pageCount;
	}

	public void setPageCount(Integer pageCount) {
		this.pageCount = pageCount;
	}

	public Integer getChunkCount() {
		return chunkCount;
	}

	public void setChunkCount(Integer chunkCount) {
		this.chunkCount = chunkCount;
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

	public Integer getEmbeddingDimensions() {
		return embeddingDimensions;
	}

	public void setEmbeddingDimensions(Integer embeddingDimensions) {
		this.embeddingDimensions = embeddingDimensions;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
