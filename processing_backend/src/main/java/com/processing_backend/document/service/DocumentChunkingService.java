package com.processing_backend.document.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.processing_backend.chunking.TextChunk;
import com.processing_backend.chunking.TokenAwareChunker;
import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentChunk;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentChunkRepository;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.ExtractedPagesStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentChunkingService {

	private static final Logger log = LoggerFactory.getLogger(DocumentChunkingService.class);

	private final DocumentRepository documentRepository;
	private final DocumentChunkRepository documentChunkRepository;
	private final ExtractedPagesStore extractedPagesStore;
	private final TokenAwareChunker tokenAwareChunker;
	private final AppProperties appProperties;
	private final DocumentChunkingService self;

	public DocumentChunkingService(
			DocumentRepository documentRepository,
			DocumentChunkRepository documentChunkRepository,
			ExtractedPagesStore extractedPagesStore,
			TokenAwareChunker tokenAwareChunker,
			AppProperties appProperties,
			@Lazy DocumentChunkingService self
	) {
		this.documentRepository = documentRepository;
		this.documentChunkRepository = documentChunkRepository;
		this.extractedPagesStore = extractedPagesStore;
		this.tokenAwareChunker = tokenAwareChunker;
		this.appProperties = appProperties;
		this.self = self;
	}

	/**
	 * Reads final page text, chunks with configured token size/overlap, persists rows.
	 * Leaves status {@link DocumentStatus#CHUNKING} for the embedding phase.
	 */
	@Transactional
	public void chunkDocument(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for chunking.", HttpStatus.NOT_FOUND));

		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			self.markChunkingFailed(documentId);
			throw new BusinessException("Local temp path missing for chunking.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (document.getS3Key() == null || document.getS3Key().isBlank()) {
			self.markChunkingFailed(documentId);
			throw new BusinessException("S3 upload must complete before chunking.", HttpStatus.CONFLICT);
		}

		document.setStatus(DocumentStatus.CHUNKING);
		documentRepository.save(document);

		Path pdfPath = Path.of(document.getLocalTempPath());
		Path finalPages = extractedPagesStore.finalPathForPdf(pdfPath);

		try {
			ExtractedDocument finalText = extractedPagesStore.read(finalPages);
			int chunkSize = Math.max(1, appProperties.getChunking().getChunkSize());
			int overlap = Math.max(0, appProperties.getChunking().getOverlap());
			if (overlap >= chunkSize) {
				overlap = Math.max(0, chunkSize - 1);
			}

			List<TextChunk> chunks = tokenAwareChunker.chunk(finalText, chunkSize, overlap);
			documentChunkRepository.deleteByDocumentId(documentId);

			List<DocumentChunk> entities = new ArrayList<>(chunks.size());
			for (TextChunk chunk : chunks) {
				DocumentChunk entity = new DocumentChunk();
				entity.setDocumentId(document.getId());
				entity.setOwnerUserId(document.getOwnerUserId());
				entity.setChunkIndex(chunk.chunkIndex());
				entity.setContent(chunk.content());
				entity.setPageStart(chunk.pageStart());
				entity.setPageEnd(chunk.pageEnd());
				entity.setTokenCount(chunk.tokenCount());
				entities.add(entity);
			}
			documentChunkRepository.saveAll(entities);

			document.setChunkCount(entities.size());
			document.setStatus(DocumentStatus.CHUNKING);
			documentRepository.save(document);

			log.info(
					"Chunking complete documentId={} chunks={} chunkSize={} overlap={}",
					documentId,
					entities.size(),
					chunkSize,
					overlap
			);
		}
		catch (BusinessException ex) {
			self.markChunkingFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markChunkingFailed(documentId);
			log.warn("Chunking failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("Document chunking failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markChunkingFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.CHUNKING_FAILED);
			documentRepository.save(doc);
		});
	}
}
