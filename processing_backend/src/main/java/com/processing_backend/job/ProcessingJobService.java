package com.processing_backend.job;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.processing_backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingJobService {

	private static final Logger log = LoggerFactory.getLogger(ProcessingJobService.class);

	private final ProcessingJobRepository processingJobRepository;
	private final AppProperties appProperties;

	public ProcessingJobService(ProcessingJobRepository processingJobRepository, AppProperties appProperties) {
		this.processingJobRepository = processingJobRepository;
		this.appProperties = appProperties;
	}

	/**
	 * Idempotent enqueue: skips if an active job of the same type already exists.
	 */
	@Transactional
	public ProcessingJob enqueue(UUID documentId, UUID ownerUserId, JobType jobType) {
		Optional<ProcessingJob> existing = processingJobRepository
				.findFirstByDocumentIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
						documentId,
						jobType,
						List.copyOf(EnumSet.of(JobStatus.PENDING, JobStatus.RUNNING))
				);
		if (existing.isPresent()) {
			log.info("Skip enqueue; active job exists documentId={} jobType={} jobId={}",
					documentId, jobType, existing.get().getId());
			return existing.get();
		}

		ProcessingJob job = new ProcessingJob();
		job.setDocumentId(documentId);
		job.setOwnerUserId(ownerUserId);
		job.setJobType(jobType);
		job.setStatus(JobStatus.PENDING);
		job.setAttempts(0);
		job.setNextRunAt(Instant.now());
		ProcessingJob saved = processingJobRepository.save(job);
		log.info("Enqueued jobId={} documentId={} jobType={}", saved.getId(), documentId, jobType);
		return saved;
	}

	@Transactional
	public Optional<ProcessingJob> claimNext() {
		List<UUID> ids = processingJobRepository.claimNextJobId();
		if (ids == null || ids.isEmpty() || ids.get(0) == null) {
			return Optional.empty();
		}
		return processingJobRepository.findById(ids.get(0));
	}

	@Transactional
	public void markSucceeded(UUID jobId) {
		processingJobRepository.findById(jobId).ifPresent(job -> {
			job.setStatus(JobStatus.SUCCEEDED);
			job.setCompletedAt(Instant.now());
			job.setLastErrorCode(null);
			processingJobRepository.save(job);
		});
	}

	@Transactional
	public void markFailedOrRetry(UUID jobId, String errorCode) {
		int maxAttempts = Math.max(1, appProperties.getJobs().getMaxAttempts());
		long delayMs = Math.max(0, appProperties.getJobs().getRetryDelayMs());

		processingJobRepository.findById(jobId).ifPresent(job -> {
			String code = errorCode == null || errorCode.isBlank() ? "PROCESSING_FAILED" : truncate(errorCode, 100);
			if (job.getAttempts() < maxAttempts) {
				job.setStatus(JobStatus.PENDING);
				job.setNextRunAt(Instant.now().plusMillis(delayMs));
				job.setLastErrorCode(code);
				job.setCompletedAt(null);
				processingJobRepository.save(job);
				log.warn("Job scheduled for retry jobId={} attempts={}/{} error={}",
						jobId, job.getAttempts(), maxAttempts, code);
			}
			else {
				job.setStatus(JobStatus.FAILED);
				job.setLastErrorCode(code);
				job.setCompletedAt(Instant.now());
				processingJobRepository.save(job);
				log.warn("Job permanently failed jobId={} attempts={} error={}", jobId, job.getAttempts(), code);
			}
		});
	}

	@Transactional
	public int recoverStaleAndExpired() {
		Instant now = Instant.now();
		int maxAttempts = Math.max(1, appProperties.getJobs().getMaxAttempts());
		Instant staleBefore = now.minusSeconds(Math.max(60, appProperties.getJobs().getStaleRunningSeconds()));
		Instant expiryBefore = now.minusSeconds(Math.max(3600, appProperties.getJobs().getJobExpirySeconds()));

		int requeued = processingJobRepository.requeueStaleRunning(staleBefore, maxAttempts, now, now);
		int failed = processingJobRepository.failStaleRunning(staleBefore, maxAttempts, now);
		int cancelled = processingJobRepository.cancelExpired(expiryBefore, now);
		if (requeued + failed + cancelled > 0) {
			log.info("Job recovery requeued={} failedStale={} cancelledExpired={}", requeued, failed, cancelled);
		}
		return requeued + failed + cancelled;
	}

	private static String truncate(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
