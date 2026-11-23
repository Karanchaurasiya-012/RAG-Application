package com.processing_backend.document.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import com.processing_backend.document.dto.DocumentUploadResponse;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.job.JobType;
import com.processing_backend.job.ProcessingJobService;
import com.processing_backend.pdf.PdfValidationService;
import com.processing_backend.pdf.ValidatedPdf;
import com.processing_backend.storage.LocalTempStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

	private static final Set<String> ALLOWED_PROVIDERS = Set.of("ollama", "gemini");
	private static final List<String> PII_ACTIONS = List.of("MASK", "KEEP_ORIGINAL");

	private final PdfValidationService pdfValidationService;
	private final LocalTempStorageService localTempStorageService;
	private final DocumentRepository documentRepository;
	private final ProcessingJobService processingJobService;
	private final AppProperties appProperties;

	public DocumentUploadService(
			PdfValidationService pdfValidationService,
			LocalTempStorageService localTempStorageService,
			DocumentRepository documentRepository,
			ProcessingJobService processingJobService,
			AppProperties appProperties
	) {
		this.pdfValidationService = pdfValidationService;
		this.localTempStorageService = localTempStorageService;
		this.documentRepository = documentRepository;
		this.processingJobService = processingJobService;
		this.appProperties = appProperties;
	}

	public DocumentUploadResponse upload(UUID ownerUserId, MultipartFile file, String embeddingProvider) {
		ValidatedPdf validated = pdfValidationService.validate(file);

		Optional<Document> existing = documentRepository
				.findByOwnerUserIdAndChecksumSha256(ownerUserId, validated.checksumSha256());
		if (existing.isPresent()) {
			Document doc = existing.get();
			if (isActiveDuplicate(doc.getStatus())) {
				return toDuplicateResponse(doc);
			}
		}

		String provider = resolveProvider(embeddingProvider);
		UUID documentId = UUID.randomUUID();
		Path tempPath = localTempStorageService.store(ownerUserId, documentId, validated.bytes());

		Document document = new Document();
		document.setId(documentId);
		document.setOwnerUserId(ownerUserId);
		document.setOriginalFilename(validated.sanitizedFilename());
		document.setContentType(validated.contentType());
		document.setFileSize(validated.bytes().length);
		document.setChecksumSha256(validated.checksumSha256());
		document.setLocalTempPath(tempPath.toString());
		document.setStatus(DocumentStatus.UPLOADED);
		document.setPiiDetected(false);
		document.setMaskingApplied(false);
		document.setPageCount(validated.pageCount());
		document.setEmbeddingProvider(provider);
		document.setEmbeddingDimensions(appProperties.getEmbedding().getDimensions());
		if ("ollama".equals(provider)) {
			document.setEmbeddingModel(appProperties.getEmbedding().getOllama().getModel());
		}
		else if ("gemini".equals(provider)) {
			document.setEmbeddingModel(appProperties.getEmbedding().getGemini().getModel());
		}

		try {
			documentRepository.save(document);
		}
		catch (RuntimeException ex) {
			localTempStorageService.deleteQuietly(tempPath);
			throw ex;
		}

		processingJobService.enqueue(documentId, ownerUserId, JobType.INITIAL_PIPELINE);
		Document saved = documentRepository.findById(documentId).orElseThrow();

		return new DocumentUploadResponse(
				saved.getId(),
				saved.getStatus(),
				saved.getOriginalFilename(),
				saved.getFileSize(),
				validated.pageCount(),
				0,
				0,
				saved.getChecksumSha256(),
				false,
				false,
				List.of(),
				List.of(),
				null,
				Boolean.TRUE
		);
	}

	private DocumentUploadResponse toDuplicateResponse(Document doc) {
		int pages = doc.getPageCount() == null ? 0 : doc.getPageCount();
		boolean hasPii = doc.isPiiDetected() || doc.getStatus() == DocumentStatus.AWAITING_PII_DECISION;
		boolean awaiting = doc.getStatus() == DocumentStatus.AWAITING_PII_DECISION;
		return new DocumentUploadResponse(
				doc.getId(),
				doc.getStatus(),
				doc.getOriginalFilename(),
				doc.getFileSize(),
				pages,
				pages,
				pages,
				doc.getChecksumSha256(),
				true,
				hasPii,
				List.of(),
				awaiting ? PII_ACTIONS : List.of(),
				awaiting ? Boolean.TRUE : null,
				doc.getStatus() != DocumentStatus.COMPLETED && doc.getStatus() != DocumentStatus.AWAITING_PII_DECISION
		);
	}

	private String resolveProvider(String embeddingProvider) {
		String provider = embeddingProvider == null || embeddingProvider.isBlank()
				? appProperties.getEmbedding().getDefaultProvider()
				: embeddingProvider.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_PROVIDERS.contains(provider)) {
			throw new BusinessException(
					"embeddingProvider must be one of: ollama, gemini.",
					HttpStatus.BAD_REQUEST
			);
		}
		return provider;
	}

	private static boolean isActiveDuplicate(DocumentStatus status) {
		return status != DocumentStatus.VALIDATION_FAILED
				&& status != DocumentStatus.EXTRACTION_FAILED
				&& status != DocumentStatus.PII_SCAN_FAILED
				&& status != DocumentStatus.CANCELLED
				&& status != DocumentStatus.EXPIRED;
	}
}
