package com.processing_backend.document.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.dto.DocumentDeleteResponse;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.job.ProcessingJobRepository;
import com.processing_backend.pdf.ExtractedPagesStore;
import com.processing_backend.storage.LocalTempStorageService;
import com.processing_backend.storage.S3ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-scoped delete with retryable S3 cleanup. DB row (and cascaded chunks/vectors/jobs)
 * is removed only after S3 delete succeeds (or there was no S3 object).
 */
@Service
public class DocumentDeleteService {

	private static final Logger log = LoggerFactory.getLogger(DocumentDeleteService.class);

	private final DocumentRepository documentRepository;
	private final ProcessingJobRepository processingJobRepository;
	private final S3ObjectStorageService s3ObjectStorageService;
	private final LocalTempStorageService localTempStorageService;
	private final ExtractedPagesStore extractedPagesStore;

	public DocumentDeleteService(
			DocumentRepository documentRepository,
			ProcessingJobRepository processingJobRepository,
			S3ObjectStorageService s3ObjectStorageService,
			LocalTempStorageService localTempStorageService,
			ExtractedPagesStore extractedPagesStore
	) {
		this.documentRepository = documentRepository;
		this.processingJobRepository = processingJobRepository;
		this.s3ObjectStorageService = s3ObjectStorageService;
		this.localTempStorageService = localTempStorageService;
		this.extractedPagesStore = extractedPagesStore;
	}

	@Transactional
	public DocumentDeleteResponse delete(UUID ownerUserId, UUID documentId) {
		Document document = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
				.orElseThrow(() -> new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		processingJobRepository.cancelActiveByDocumentId(documentId, Instant.now());

		boolean s3Deleted = false;
		String s3Key = document.getS3Key();
		if (s3Key != null && !s3Key.isBlank()) {
			// Fail here so the client can retry; do not drop DB metadata while S3 object remains.
			s3ObjectStorageService.deleteOriginalPdf(s3Key);
			s3Deleted = true;
		}

		boolean localDeleted = false;
		if (document.getLocalTempPath() != null && !document.getLocalTempPath().isBlank()) {
			Path pdfPath = Path.of(document.getLocalTempPath());
			extractedPagesStore.deleteQuietly(extractedPagesStore.pathForPdf(pdfPath));
			extractedPagesStore.deleteQuietly(extractedPagesStore.finalPathForPdf(pdfPath));
			localTempStorageService.deleteQuietly(pdfPath);
			localDeleted = true;
		}

		documentRepository.delete(document);
		log.info(
				"Document deleted documentId={} s3Deleted={} localDeleted={}",
				documentId,
				s3Deleted,
				localDeleted
		);

		return new DocumentDeleteResponse(documentId, true, s3Deleted, localDeleted);
	}
}
