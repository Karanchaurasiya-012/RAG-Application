package com.processing_backend.document.service;

import java.nio.file.Path;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.storage.S3ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentS3UploadService {

	private static final Logger log = LoggerFactory.getLogger(DocumentS3UploadService.class);

	private final DocumentRepository documentRepository;
	private final S3ObjectStorageService s3ObjectStorageService;
	private final DocumentS3UploadService self;

	public DocumentS3UploadService(
			DocumentRepository documentRepository,
			S3ObjectStorageService s3ObjectStorageService,
			@Lazy DocumentS3UploadService self
	) {
		this.documentRepository = documentRepository;
		this.s3ObjectStorageService = s3ObjectStorageService;
		this.self = self;
	}

	@Transactional
	public void uploadOriginalPdf(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for S3 upload.", HttpStatus.NOT_FOUND));

		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			self.markUploadFailed(documentId);
			throw new BusinessException("Local original PDF path missing for S3 upload.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (document.getS3Key() != null && !document.getS3Key().isBlank()) {
			log.info("S3 object already present for documentId={} key={}", documentId, document.getS3Key());
			return;
		}

		document.setStatus(DocumentStatus.UPLOADING_S3);
		documentRepository.save(document);

		String objectKey = s3ObjectStorageService.buildObjectKey(document.getOwnerUserId(), document.getId());
		try {
			s3ObjectStorageService.putOriginalPdf(
					Path.of(document.getLocalTempPath()),
					objectKey,
					document.getChecksumSha256(),
					document.getFileSize()
			);
			document.setS3Bucket(s3ObjectStorageService.bucket());
			document.setS3Key(objectKey);
			// Remain UPLOADING_S3 until chunking service advances status.
			documentRepository.save(document);
			log.info("Document S3 metadata saved documentId={} bucket={} key={}", documentId, document.getS3Bucket(), objectKey);
		}
		catch (BusinessException ex) {
			self.markUploadFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markUploadFailed(documentId);
			log.warn("S3 upload failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("Failed to upload original PDF to S3.", HttpStatus.BAD_GATEWAY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markUploadFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.UPLOAD_FAILED);
			documentRepository.save(doc);
		});
	}
}
