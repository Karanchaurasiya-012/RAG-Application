package com.processing_backend.document.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiFinding;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.document.repository.PiiFindingRepository;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.ExtractedPagesStore;
import com.processing_backend.pii.PiiDetectionResult;
import com.processing_backend.pii.PiiDetectionService;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPiiScanService {

	private static final Logger log = LoggerFactory.getLogger(DocumentPiiScanService.class);

	private final DocumentRepository documentRepository;
	private final PiiFindingRepository piiFindingRepository;
	private final ExtractedPagesStore extractedPagesStore;
	private final PiiDetectionService piiDetectionService;
	private final DocumentPiiScanService self;

	public DocumentPiiScanService(
			DocumentRepository documentRepository,
			PiiFindingRepository piiFindingRepository,
			ExtractedPagesStore extractedPagesStore,
			PiiDetectionService piiDetectionService,
			@Lazy DocumentPiiScanService self
	) {
		this.documentRepository = documentRepository;
		this.piiFindingRepository = piiFindingRepository;
		this.extractedPagesStore = extractedPagesStore;
		this.piiDetectionService = piiDetectionService;
		this.self = self;
	}

	@Transactional
	public PiiDetectionResult scan(UUID documentId) {
		Document document = documentRepository.findById(documentId)
				.orElseThrow(() -> new BusinessException("Document not found for PII scan.", HttpStatus.NOT_FOUND));

		if (document.getLocalTempPath() == null || document.getLocalTempPath().isBlank()) {
			self.markPiiScanFailed(documentId);
			throw new BusinessException("Extracted pages are missing for PII scan.", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		document.setStatus(DocumentStatus.PII_SCANNING);
		documentRepository.save(document);

		try {
			var pagesPath = extractedPagesStore.pathForPdf(java.nio.file.Path.of(document.getLocalTempPath()));
			ExtractedDocument extracted = extractedPagesStore.read(pagesPath);
			PiiDetectionResult result = piiDetectionService.detect(extracted);

			piiFindingRepository.deleteByDocumentId(documentId);
			piiFindingRepository.saveAll(toFindings(documentId, result.spans()));

			document.setPiiDetected(result.hasPii());
			if (result.hasPii()) {
				document.setStatus(DocumentStatus.AWAITING_PII_DECISION);
			}
			else {
				document.setStatus(DocumentStatus.NO_PII_DETECTED);
			}
			documentRepository.save(document);

			log.info(
					"PII scan persisted documentId={} hasPii={} findingGroups={}",
					documentId,
					result.hasPii(),
					result.summary().size()
			);
			return result;
		}
		catch (BusinessException ex) {
			self.markPiiScanFailed(documentId);
			throw ex;
		}
		catch (RuntimeException ex) {
			self.markPiiScanFailed(documentId);
			log.warn("PII scan failed documentId={} type={}", documentId, ex.getClass().getSimpleName());
			throw new BusinessException("PII detection failed.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPiiScanFailed(UUID documentId) {
		documentRepository.findById(documentId).ifPresent(doc -> {
			doc.setStatus(DocumentStatus.PII_SCAN_FAILED);
			documentRepository.save(doc);
		});
	}

	/**
	 * Persist metadata only: type + page + confidence + occurrence count. No raw PII values.
	 */
	private static List<PiiFinding> toFindings(UUID documentId, List<PiiSpan> spans) {
		record Key(PiiEntityType type, int page) {
		}
		Map<Key, Agg> grouped = new LinkedHashMap<>();
		for (PiiSpan span : spans) {
			Key key = new Key(span.entityType(), span.pageNumber());
			Agg agg = grouped.computeIfAbsent(key, ignored -> new Agg());
			agg.count++;
			agg.confidenceSum += span.confidence();
		}

		List<PiiFinding> findings = new ArrayList<>();
		for (Map.Entry<Key, Agg> entry : grouped.entrySet()) {
			PiiFinding finding = new PiiFinding();
			finding.setDocumentId(documentId);
			finding.setEntityType(entry.getKey().type());
			finding.setPageNumber(entry.getKey().page());
			finding.setOccurrenceCount(entry.getValue().count);
			finding.setConfidence(entry.getValue().confidenceSum / entry.getValue().count);
			findings.add(finding);
		}
		return findings;
	}

	private static final class Agg {
		private int count;
		private double confidenceSum;
	}
}
