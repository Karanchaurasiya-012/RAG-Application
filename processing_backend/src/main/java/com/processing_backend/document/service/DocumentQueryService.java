package com.processing_backend.document.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.dto.DocumentDetailsResponse;
import com.processing_backend.document.dto.DocumentListItemResponse;
import com.processing_backend.document.dto.DocumentListResponse;
import com.processing_backend.document.dto.DocumentStatusResponse;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiFinding;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.document.repository.PiiFindingRepository;
import com.processing_backend.job.ProcessingJob;
import com.processing_backend.job.ProcessingJobRepository;
import com.processing_backend.pii.PiiEntityType;
import com.processing_backend.pii.PiiSummaryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryService {

	private static final List<String> PII_ACTIONS = List.of("MASK", "KEEP_ORIGINAL");

	private final DocumentRepository documentRepository;
	private final PiiFindingRepository piiFindingRepository;
	private final ProcessingJobRepository processingJobRepository;

	public DocumentQueryService(
			DocumentRepository documentRepository,
			PiiFindingRepository piiFindingRepository,
			ProcessingJobRepository processingJobRepository
	) {
		this.documentRepository = documentRepository;
		this.piiFindingRepository = piiFindingRepository;
		this.processingJobRepository = processingJobRepository;
	}

	@Transactional(readOnly = true)
	public DocumentStatusResponse getStatus(UUID ownerUserId, UUID documentId) {
		Document document = requireOwned(ownerUserId, documentId);
		ProcessingJob job = processingJobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId).orElse(null);
		return new DocumentStatusResponse(
				document.getId(),
				document.getStatus(),
				document.isPiiDetected(),
				document.getPiiDecision(),
				document.isMaskingApplied(),
				document.getChunkCount(),
				document.getEmbeddingProvider(),
				hasDownload(document),
				job == null ? null : job.getStatus().name(),
				job == null ? null : job.getJobType().name(),
				job == null ? null : job.getAttempts(),
				job == null ? null : job.getLastErrorCode()
		);
	}

	@Transactional(readOnly = true)
	public DocumentListResponse list(UUID ownerUserId, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(100, Math.max(1, size));
		Page<Document> result = documentRepository.findByOwnerUserIdOrderByCreatedAtDesc(
				ownerUserId,
				PageRequest.of(safePage, safeSize)
		);
		List<DocumentListItemResponse> items = result.getContent().stream()
				.map(doc -> new DocumentListItemResponse(
						doc.getId(),
						doc.getStatus(),
						doc.getOriginalFilename(),
						doc.getFileSize(),
						doc.getPageCount(),
						doc.isPiiDetected(),
						doc.getPiiDecision(),
						doc.isMaskingApplied(),
						doc.getChunkCount(),
						doc.getEmbeddingProvider(),
						doc.getCreatedAt(),
						doc.getUpdatedAt()
				))
				.toList();
		return new DocumentListResponse(items, safePage, safeSize, result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public DocumentDetailsResponse getDetails(UUID ownerUserId, UUID documentId) {
		Document document = requireOwned(ownerUserId, documentId);
		List<PiiSummaryItem> summary = toPiiSummary(piiFindingRepository.findByDocumentId(documentId));
		boolean awaiting = document.getStatus() == DocumentStatus.AWAITING_PII_DECISION;
		return new DocumentDetailsResponse(
				document.getId(),
				document.getStatus(),
				document.getOriginalFilename(),
				document.getContentType(),
				document.getFileSize(),
				document.getChecksumSha256(),
				document.getPageCount(),
				document.getChunkCount(),
				document.isPiiDetected(),
				document.getPiiDecision(),
				document.isMaskingApplied(),
				summary,
				awaiting ? PII_ACTIONS : List.of(),
				true,
				hasDownload(document),
				document.getEmbeddingProvider(),
				document.getEmbeddingModel(),
				document.getEmbeddingDimensions(),
				document.getCreatedAt(),
				document.getUpdatedAt()
		);
	}

	private Document requireOwned(UUID ownerUserId, UUID documentId) {
		return documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
				.orElseThrow(() -> new BusinessException("Document not found.", HttpStatus.NOT_FOUND));
	}

	private static boolean hasDownload(Document document) {
		return document.getS3Key() != null && !document.getS3Key().isBlank();
	}

	/**
	 * Aggregates finding metadata only — never includes raw matched PII values.
	 */
	static List<PiiSummaryItem> toPiiSummary(List<PiiFinding> findings) {
		Map<PiiEntityType, Aggregated> map = new LinkedHashMap<>();
		for (PiiFinding finding : findings) {
			Aggregated agg = map.computeIfAbsent(finding.getEntityType(), t -> new Aggregated());
			agg.count += Math.max(1, finding.getOccurrenceCount());
			if (finding.getPageNumber() != null && !agg.pages.contains(finding.getPageNumber())) {
				agg.pages.add(finding.getPageNumber());
			}
		}
		List<PiiSummaryItem> out = new ArrayList<>();
		map.forEach((type, agg) -> out.add(new PiiSummaryItem(type, agg.count, List.copyOf(agg.pages))));
		return out;
	}

	private static final class Aggregated {
		private int count;
		private final List<Integer> pages = new ArrayList<>();
	}
}
