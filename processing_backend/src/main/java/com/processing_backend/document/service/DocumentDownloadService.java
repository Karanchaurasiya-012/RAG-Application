package com.processing_backend.document.service;

import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.dto.DocumentDownloadResponse;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.storage.S3ObjectStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentDownloadService {

	private final DocumentRepository documentRepository;
	private final S3ObjectStorageService s3ObjectStorageService;

	public DocumentDownloadService(
			DocumentRepository documentRepository,
			S3ObjectStorageService s3ObjectStorageService
	) {
		this.documentRepository = documentRepository;
		this.s3ObjectStorageService = s3ObjectStorageService;
	}

	@Transactional(readOnly = true)
	public DocumentDownloadResponse createAuthorizedDownload(UUID ownerUserId, UUID documentId) {
		Document document = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
				.orElseThrow(() -> new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		if (document.getS3Key() == null || document.getS3Key().isBlank()) {
			throw new BusinessException("Original PDF is not available for download yet.", HttpStatus.CONFLICT);
		}

		String url = s3ObjectStorageService.createDownloadUrl(document.getS3Key());
		return new DocumentDownloadResponse(
				document.getId(),
				document.getOriginalFilename(),
				url,
				s3ObjectStorageService.downloadExpirySeconds()
		);
	}
}
