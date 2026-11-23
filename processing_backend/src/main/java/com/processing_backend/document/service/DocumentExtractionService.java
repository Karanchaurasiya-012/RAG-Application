package com.processing_backend.document.service;

import java.nio.file.Path;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.ExtractedPagesStore;
import com.processing_backend.pdf.PdfExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentExtractionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentExtractionService.class);

	private final DocumentRepository documentRepository;
	private final PdfExtractionService pdfExtractionService;
	private final ExtractedPagesStore extractedPagesStore;
	private final DocumentExtractionService self;

	public DocumentExtractionService(
			DocumentRepository documentRepository,
			PdfExtractionService pdfExtractionService,
			ExtractedPagesStore extractedPagesStore,
			@Lazy DocumentExtractionService self
	) {
		this.documentRepository = documentRepository;
		this.pdfExtractionService = pdfExtractionService;
		this.extractedPagesStore = extractedPagesStore;
		this.self = self;
	}

	/**
	 * Extracts page text from the local temp PDF and stores it beside the PDF.
	 * Does not log extracted content. Leaves document ready for Phase 6 PII scan.
	 */
	@Transactional
	public ExtractedDocument extractAndStore(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for extraction.", HttpStatus.NOT_FOUND));

		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			self.markExtractionFailed(documentId);
			throw new BusinessException("PDF temp file is missing for extraction.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		document.setStatus(DocumentStatus.EXTRACTING);
		documentRepository.save(document);

		Path pdfPath = Path.of(document.getLocalTempPath());
		Path pagesPath = null;
		try {
			ExtractedDocument extracted = pdfExtractionService.extractFromPath(pdfPath);
			pagesPath = extractedPagesStore.write(pdfPath, extracted);
			document.setPageCount(extracted.pageCount());
			document.setStatus(DocumentStatus.UPLOADED);
			documentRepository.save(document);
			log.info(
					"Document extraction complete documentId={} pageCount={} nonEmptyPages={}",
					document.getId(),
					extracted.pageCount(),
					extracted.nonEmptyPageCount()
			);
			return extracted;
		}
		catch (BusinessException ex) {
			extractedPagesStore.deleteQuietly(pagesPath);
			self.markExtractionFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			extractedPagesStore.deleteQuietly(pagesPath);
			self.markExtractionFailed(documentId);
			log.warn("Document extraction failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("PDF text extraction failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markExtractionFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.EXTRACTION_FAILED);
			documentRepository.save(doc);
		});
	}
}
