package com.processing_backend.document.service;

import java.util.UUID;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.document.dto.PiiDecisionResponse;
import com.processing_backend.document.entity.Document;
import com.processing_backend.document.entity.DocumentStatus;
import com.processing_backend.document.entity.PiiDecision;
import com.processing_backend.document.repository.DocumentRepository;
import com.processing_backend.job.JobType;
import com.processing_backend.job.ProcessingJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPiiDecisionService {

	private static final Logger log = LoggerFactory.getLogger(DocumentPiiDecisionService.class);

	private final DocumentRepository documentRepository;
	private final ProcessingJobService processingJobService;

	public DocumentPiiDecisionService(
			DocumentRepository documentRepository,
			ProcessingJobService processingJobService
	) {
		this.documentRepository = documentRepository;
		this.processingJobService = processingJobService;
	}

	@Transactional
	public PiiDecisionResponse submitDecision(UUID ownerUserId, UUID documentId, PiiDecision decision) {
		Document document = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
				.orElseThrow(() -> new BusinessException("Document not found.", HttpStatus.NOT_FOUND));

		if (document.getStatus() != DocumentStatus.AWAITING_PII_DECISION) {
			throw new BusinessException(
					"PII decision is only allowed when status is AWAITING_PII_DECISION.",
					HttpStatus.CONFLICT
			);
		}

		if (document.getPiiDecision() != null && !document.getPiiDecision().isBlank()) {
			throw new BusinessException(
					"PII decision already recorded and cannot be overwritten.",
					HttpStatus.CONFLICT
			);
		}

		document.setPiiDecision(decision.name());
		document.setStatus(DocumentStatus.PREPARING_TEXT);
		documentRepository.save(document);

		processingJobService.enqueue(documentId, ownerUserId, JobType.POST_PII_PIPELINE);

		log.info("PII decision accepted documentId={} decision={} asyncJob={}", documentId, decision, JobType.POST_PII_PIPELINE);

		return new PiiDecisionResponse(
				document.getId(),
				document.getStatus(),
				decision,
				true,
				"PII decision recorded. Remaining processing continues asynchronously."
		);
	}
}
