package com.processing_backend.document.service;

import java.util.List;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentChunk;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentChunkRepository;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.embedding.ChunkEmbeddingStore;
import com.processing_backend.embedding.EmbeddingService;
import com.processing_backend.embedding.EmbeddingVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentEmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);

	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository documentChunkRepository;
	private final EmbeddingService embeddingService;
	private final ChunkEmbeddingStore chunkEmbeddingStore;
	private final DocumentEmbeddingService self;

	public DocumentEmbeddingService(
			DocumentRepository documentRepository,
			DocumentChunkRepository documentChunkRepository,
			EmbeddingService embeddingService,
			ChunkEmbeddingStore chunkEmbeddingStore,
			@Lazy DocumentEmbeddingService self
	) {
		this.documentRepository = documentRepository;
		this.documentChunkRepository = documentChunkRepository;
		this.embeddingService = embeddingService;
		this.chunkEmbeddingStore = chunkEmbeddingStore;
		this.self = self;
	}

	/**
	 * Embeds all chunks with the document's selected provider and writes VECTOR values.
	 * Leaves status {@link DocumentStatus#EMBEDDING} for Phase 13 verification/completion.
	 */
	public void embedDocument(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for embedding.", HttpStatus.NOT_FOUND));

		if (document.getStatus() != DocumentStatus.CHUNKING
				&& document.getStatus() != DocumentStatus.EMBEDDING) {
			throw new BusinessException(
					"Embedding is only allowed after chunking completes.",
					HttpStatus.CONFLICT
			);
		}

		if (document.getChunkCount() == null || document.getChunkCount() < 1) {
			self.markEmbeddingFailed(documentId);
			throw new BusinessException("No chunks available for embedding.", HttpStatus.UNPROCESSABLE_ENTITY);
		}

		String provider = document.getEmbeddingProvider();
		if (provider == null || provider.isBlank()) {
			self.markEmbeddingFailed(documentId);
			throw new BusinessException("Document embedding provider is missing.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		self.markEmbedding(documentId);

		List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
		if (chunks.isEmpty()) {
			self.markEmbeddingFailed(documentId);
			throw new BusinessException("No chunk rows found for embedding.", HttpStatus.UNPROCESSABLE_ENTITY);
		}

		try {
			String modelUsed = null;
			int dimensions = 0;
			for (DocumentChunk chunk : chunks) {
				EmbeddingVector vector = embeddingService.embed(provider, chunk.getContent());
				chunkEmbeddingStore.saveEmbedding(chunk.getId(), vector);
				modelUsed = vector.model();
				dimensions = vector.dimensions();
			}
			self.finalizeEmbedding(documentId, provider, modelUsed, dimensions);
			log.info(
					"Embedding complete documentId={} provider={} model={} dims={} chunks={}",
					documentId,
					provider,
					modelUsed,
					dimensions,
					chunks.size()
			);
		}
		catch (BusinessException ex) {
			self.markEmbeddingFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markEmbeddingFailed(documentId);
			log.warn("Embedding failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("Document embedding failed.", HttpStatus.BAD_GATEWAY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markEmbedding(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.EMBEDDING);
			documentRepository.save(doc);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void finalizeEmbedding(UUID documentId, String provider, String model, int dimensions) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.EMBEDDING);
			doc.setEmbeddingProvider(provider);
			if (model != null && !model.isBlank()) {
				doc.setEmbeddingModel(model);
			}
			doc.setEmbeddingDimensions(dimensions);
			documentRepository.save(doc);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markEmbeddingFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.EMBEDDING_FAILED);
			documentRepository.save(doc);
		});
	}
}
