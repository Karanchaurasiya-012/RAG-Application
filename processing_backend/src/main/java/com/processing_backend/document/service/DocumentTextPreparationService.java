package com.processing_backend.document.service;

import java.nio.file.Path;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiDecision;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.ExtractedPagesStore;
import com.processing_backend.pii.PiiDetectionResult;
import com.processing_backend.pii.PiiDetectionService;
import com.processing_backend.pii.PiiMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTextPreparationService {

	private static final Logger log = LoggerFactory.getLogger(DocumentTextPreparationService.class);

	private final DocumentRepository documentRepository;
	private final ExtractedPagesStore extractedPagesStore;
	private final PiiDetectionService piiDetectionService;
	private final PiiMaskingService piiMaskingService;
	private final DocumentTextPreparationService self;

	public DocumentTextPreparationService(
			DocumentRepository documentRepository,
			ExtractedPagesStore extractedPagesStore,
			PiiDetectionService piiDetectionService,
			PiiMaskingService piiMaskingService,
			@Lazy DocumentTextPreparationService self
	) {
		this.documentRepository = documentRepository;
		this.extractedPagesStore = extractedPagesStore;
		this.piiDetectionService = piiDetectionService;
		this.piiMaskingService = piiMaskingService;
		this.self = self;
	}

	/**
	 * Builds the final extracted text used by later chunking:
	 * MASK → placeholders; KEEP_ORIGINAL / no-PII → unchanged extracted text.
	 * Original PDF is never modified.
	 */
	@Transactional
	public void prepareFinalText(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for text preparation.", HttpStatus.NOT_FOUND));

		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			self.markMaskingFailed(documentId);
			throw new BusinessException("Local temp PDF path missing for text preparation.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (document.getStatus() != DocumentStatus.PREPARING_TEXT
				&& document.getStatus() != DocumentStatus.NO_PII_DETECTED
				&& document.getStatus() != DocumentStatus.MASKING_FAILED) {
			throw new BusinessException(
					"Text preparation is only allowed after PII decision or when no PII was detected.",
					HttpStatus.CONFLICT
			);
		}

		document.setStatus(DocumentStatus.PREPARING_TEXT);
		documentRepository.save(document);

		Path pdfPath = Path.of(document.getLocalTempPath());
		try {
			ExtractedDocument extracted = extractedPagesStore.read(extractedPagesStore.pathForPdf(pdfPath));
			ExtractedDocument finalText;
			boolean maskingApplied = false;

			PiiDecision decision = parseDecision(document.getPiiDecision());
			if (decision == PiiDecision.MASK) {
				PiiDetectionResult detection = piiDetectionService.detect(extracted);
				finalText = piiMaskingService.mask(extracted, detection.spans());
				maskingApplied = true;
			}
			else {
				// KEEP_ORIGINAL or no-PII auto path
				finalText = extracted;
			}

			extractedPagesStore.writeFinal(pdfPath, finalText);
			document.setMaskingApplied(maskingApplied);
			// Ready for Phase 9 S3 upload of the original PDF.
			document.setStatus(DocumentStatus.PREPARING_TEXT);
			documentRepository.save(document);

			log.info(
					"Final text prepared documentId={} maskingApplied={} pages={}",
					documentId,
					maskingApplied,
					finalText.pageCount()
			);
		}
		catch (BusinessException ex) {
			self.markMaskingFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markMaskingFailed(documentId);
			log.warn("Text preparation failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("Extracted-text masking/preparation failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markMaskingFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.MASKING_FAILED);
			documentRepository.save(doc);
		});
	}

	private static PiiDecision parseDecision(String raw) {
		if (raw == null || raw.isBlank()) {
			return PiiDecision.KEEP_ORIGINAL;
		}
		try {
			return PiiDecision.valueOf(raw);
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException("Invalid stored PII decision.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
