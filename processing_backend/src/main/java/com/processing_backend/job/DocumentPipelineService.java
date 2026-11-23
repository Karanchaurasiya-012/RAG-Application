package com.processing_backend.job;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.document.service.DocumentChunkingService;
import com.processing_backend.document.service.DocumentEmbeddingService;
import com.processing_backend.document.service.DocumentExtractionService;
import com.processing_backend.document.service.DocumentPiiScanService;
import com.processing_backend.document.service.DocumentS3UploadService;
import com.processing_backend.document.service.DocumentTextPreparationService;
import com.processing_backend.document.service.DocumentVectorStorageService;
import com.processing_backend.embedding.ChunkEmbeddingStore;
import com.processing_backend.pdf.ExtractedPagesStore;
import com.processing_backend.pii.PiiDetectionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Idempotent pipeline steps driven by durable document/chunk state.
 * S3 and PostgreSQL are not one atomic unit — partial progress is retryable.
 */
@Service
public class DocumentPipelineService {

	private static final Logger log = LoggerFactory.getLogger(DocumentPipelineService.class);

	private final DocumentRepository documentRepository;
	private final DocumentExtractionService documentExtractionService;
	private final DocumentPiiScanService documentPiiScanService;
	private final DocumentTextPreparationService documentTextPreparationService;
	private final DocumentS3UploadService documentS3UploadService;
	private final DocumentChunkingService documentChunkingService;
	private final DocumentEmbeddingService documentEmbeddingService;
	private final DocumentVectorStorageService documentVectorStorageService;
	private final ExtractedPagesStore extractedPagesStore;
	private final ChunkEmbeddingStore chunkEmbeddingStore;

	public DocumentPipelineService(
			DocumentRepository documentRepository,
			DocumentExtractionService documentExtractionService,
			DocumentPiiScanService documentPiiScanService,
			DocumentTextPreparationService documentTextPreparationService,
			DocumentS3UploadService documentS3UploadService,
			DocumentChunkingService documentChunkingService,
			DocumentEmbeddingService documentEmbeddingService,
			DocumentVectorStorageService documentVectorStorageService,
			ExtractedPagesStore extractedPagesStore,
			ChunkEmbeddingStore chunkEmbeddingStore
	) {
		this.documentRepository = documentRepository;
		this.documentExtractionService = documentExtractionService;
		this.documentPiiScanService = documentPiiScanService;
		this.documentTextPreparationService = documentTextPreparationService;
		this.documentS3UploadService = documentS3UploadService;
		this.documentChunkingService = documentChunkingService;
		this.documentEmbeddingService = documentEmbeddingService;
		this.documentVectorStorageService = documentVectorStorageService;
		this.extractedPagesStore = extractedPagesStore;
		this.chunkEmbeddingStore = chunkEmbeddingStore;
	}

	public void runInitialPipeline(UUID documentId) {
		Document document = requireDocument(documentId);
		if (isTerminalWaitOrDone(document.getStatus())) {
			log.info("Initial pipeline noop documentId={} status={}", documentId, document.getStatus());
			return;
		}

		if (!hasExtractedPages(document)) {
			documentExtractionService.extractAndStore(documentId);
			document = requireDocument(documentId);
		}

		if (document.getStatus() != DocumentStatus.AWAITING_PII_DECISION
				&& document.getStatus() != DocumentStatus.NO_PII_DETECTED
				&& document.getStatus() != DocumentStatus.PREPARING_TEXT
				&& document.getStatus() != DocumentStatus.COMPLETED
				&& document.getPiiDecision() == null) {
			PiiDetectionResult result = documentPiiScanService.scan(documentId);
			document = requireDocument(documentId);
			if (result.hasPii() || document.getStatus() == DocumentStatus.AWAITING_PII_DECISION) {
				log.info("Initial pipeline waiting for PII decision documentId={}", documentId);
				return;
			}
		}

		if (document.getStatus() == DocumentStatus.AWAITING_PII_DECISION) {
			return;
		}

		runPostDecisionSteps(documentId);
	}

	public void runPostPiiPipeline(UUID documentId) {
		Document document = requireDocument(documentId);
		if (document.getStatus() == DocumentStatus.COMPLETED) {
			log.info("Post-PII pipeline noop documentId={} already COMPLETED", documentId);
			return;
		}
		if (document.getPiiDecision() == null || document.getPiiDecision().isBlank()) {
			throw new BusinessException("PII decision missing for post-PII pipeline.", HttpStatus.CONFLICT);
		}
		runPostDecisionSteps(documentId);
	}

	private void runPostDecisionSteps(UUID documentId) {
		Document document = requireDocument(documentId);
		if (document.getStatus() == DocumentStatus.COMPLETED) {
			return;
		}

		if (!hasFinalPages(document) || document.getStatus() == DocumentStatus.MASKING_FAILED
				|| document.getStatus() == DocumentStatus.NO_PII_DETECTED
				|| document.getStatus() == DocumentStatus.PREPARING_TEXT) {
			documentTextPreparationService.prepareFinalText(documentId);
			document = requireDocument(documentId);
		}

		if (document.getS3Key() == null || document.getS3Key().isBlank()
				|| document.getStatus() == DocumentStatus.UPLOAD_FAILED) {
			documentS3UploadService.uploadOriginalPdf(documentId);
			document = requireDocument(documentId);
		}

		int chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
		if (chunkCount < 1 || document.getStatus() == DocumentStatus.CHUNKING_FAILED) {
			documentChunkingService.chunkDocument(documentId);
			document = requireDocument(documentId);
			chunkCount = document.getChunkCount() == null ? 0 : document.getChunkCount();
		}

		int embedded = chunkEmbeddingStore.countEmbeddedChunks(documentId);
		if (embedded < chunkCount || document.getStatus() == DocumentStatus.EMBEDDING_FAILED) {
			documentEmbeddingService.embedDocument(documentId);
			document = requireDocument(documentId);
		}

		if (document.getStatus() != DocumentStatus.COMPLETED) {
			documentVectorStorageService.storeAndComplete(documentId);
		}
	}

	private boolean hasExtractedPages(Document document) {
		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			return false;
		}
		Path pages = extractedPagesStore.pathForPdf(Path.of(document.getLocalTempPath()));
		return Files.isRegularFile(pages);
	}

	private boolean hasFinalPages(Document document) {
		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			return false;
		}
		Path pages = extractedPagesStore.finalPathForPdf(Path.of(document.getLocalTempPath()));
		return Files.isRegularFile(pages);
	}

	private static boolean isTerminalWaitOrDone(DocumentStatus status) {
		return status == DocumentStatus.COMPLETED
				|| status == DocumentStatus.AWAITING_PII_DECISION
				|| status == DocumentStatus.CANCELLED
				|| status == DocumentStatus.EXPIRED;
	}

	private Document requireDocument(UUID documentId) {
		return documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for pipeline.", HttpStatus.NOT_FOUND));
	}
}
