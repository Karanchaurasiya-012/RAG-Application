package com.processing_backend.document.entity;

import java.time.Instant;
import java.util.UUID;

import com.processing_backend.pii.PiiEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "pii_findings")
public class PiiFinding {

	@Id
	private UUID id;

	@Column(name = "document_id", nullable = false)
	private UUID documentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "entity_type", nullable = false, length = 50)
	private PiiEntityType entityType;

	@Column(name = "page_number")
	private Integer pageNumber;

	@Column
	private Double confidence;

	@Column(name = "occurrence_count", nullable = false)
	private int occurrenceCount;

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

	public PiiEntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(PiiEntityType entityType) {
		this.entityType = entityType;
	}

	public Integer getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(Integer pageNumber) {
		this.pageNumber = pageNumber;
	}

	public Double getConfidence() {
		return confidence;
	}

	public void setConfidence(Double confidence) {
		this.confidence = confidence;
	}

	public int getOccurrenceCount() {
		return occurrenceCount;
	}

	public void setOccurrenceCount(int occurrenceCount) {
		this.occurrenceCount = occurrenceCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
