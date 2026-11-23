package com.processing_backend.document.service;

import java.util.List;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.embedding.ChunkEmbeddingStore;
import com.processing_backend.embedding.VectorPersistenceValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 13: verify persisted pgvector embeddings, then mark the document COMPLETED.
 * A document cannot complete without S3 original + all chunk vectors.
 */
@Service
public class DocumentVectorStorageService {

	private static final Logger log = LoggerFactory.getLogger(DocumentVectorStorageService.class);

	private final DocumentRepository documentRepository;
	private final ChunkEmbeddingStore chunkEmbeddingStore;
	private final VectorPersistenceValidator vectorPersistenceValidator;
	private final AppProperties appProperties;
	private final DocumentVectorStorageService self;

	public DocumentVectorStorageService(
			DocumentRepository documentRepository,
			ChunkEmbeddingStore chunkEmbeddingStore,
			VectorPersistenceValidator vectorPersistenceValidator,
			AppProperties appProperties,
			@Lazy DocumentVectorStorageService self
	) {
		this.documentRepository = documentRepository;
		this.chunkEmbeddingStore = chunkEmbeddingStore;
		this.vectorPersistenceValidator = vectorPersistenceValidator;
		this.appProperties = appProperties;
		this.self = self;
	}

	public void storeAndComplete(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for vector storage.", HttpStatus.NOT_FOUND));

		if (document.getStatus() != DocumentStatus.EMBEDDING
				&& document.getStatus() != DocumentStatus.STORING_VECTORS) {
			throw new BusinessException(
					"Vector storage is only allowed after embeddings are generated.",
					HttpStatus.CONFLICT
			);
		}

		self.markStoring(documentId);

		try {
			if (document.getS3Key() == null || document.getS3Key().isBlank()) {
				throw new BusinessException(
						"Original PDF must be stored in S3 before completion.",
						HttpStatus.CONFLICT
				);
			}

			int expectedDims = document.getEmbeddingDimensions() != null
					? document.getEmbeddingDimensions()
					: appProperties.getEmbedding().getDimensions();
			if (expectedDims != appProperties.getEmbedding().getDimensions()) {
				throw new BusinessException(
						"Document embedding dimensions do not match configured VECTOR size "
								+ appProperties.getEmbedding().getDimensions() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}

			List<ChunkEmbeddingStore.ChunkVectorRow> rows = chunkEmbeddingStore.loadVectorMetadata(documentId);
			vectorPersistenceValidator.requirePersisted(
					document.getOwnerUserId(),
					document.getEmbeddingProvider(),
					document.getEmbeddingModel(),
					expectedDims,
					document.getChunkCount() == null ? 0 : document.getChunkCount(),
					rows
			);

			String probeLiteral = chunkEmbeddingStore.loadFirstVectorLiteral(documentId);
			if (probeLiteral == null || probeLiteral.isBlank()) {
				throw new BusinessException(
						"No embedding literal available for similarity probe.",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			List<ChunkEmbeddingStore.SimilarChunkHit> hits = chunkEmbeddingStore.findSimilar(
					document.getOwnerUserId(),
					document.getEmbeddingProvider(),
					probeLiteral,
					Math.min(3, rows.size())
			);
			if (hits.isEmpty()) {
				throw new BusinessException(
						"pgvector similarity probe returned no rows after persistence.",
						HttpStatus.INTERNAL_SERVER_ERROR
				);
			}

			self.markCompleted(documentId);
			log.info(
					"Vector storage complete documentId={} provider={} dims={} chunks={} nnHits={}",
					documentId,
					document.getEmbeddingProvider(),
					expectedDims,
					rows.size(),
					hits.size()
			);
		}
		catch (BusinessException ex) {
			self.markVectorStorageFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markVectorStorageFailed(documentId);
			log.warn("Vector storage failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("Vector persistence failed.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markStoring(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.STORING_VECTORS);
			documentRepository.save(doc);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markCompleted(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.COMPLETED);
			documentRepository.save(doc);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markVectorStorageFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.VECTOR_STORAGE_FAILED);
			documentRepository.save(doc);
		});
	}
}
