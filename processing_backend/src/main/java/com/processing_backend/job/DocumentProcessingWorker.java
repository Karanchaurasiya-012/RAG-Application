package com.processing_backend.job;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.processing_backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessingWorker {

	private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

	private final ProcessingJobService processingJobService;
	private final DocumentPipelineService documentPipelineService;
	private final Executor documentJobExecutor;
	private final AtomicBoolean dispatching = new AtomicBoolean(false);

	public DocumentProcessingWorker(
			ProcessingJobService processingJobService,
			DocumentPipelineService documentPipelineService,
			@Qualifier("documentJobExecutor") Executor documentJobExecutor
	) {
		this.processingJobService = processingJobService;
		this.documentPipelineService = documentPipelineService;
		this.documentJobExecutor = documentJobExecutor;
	}

	@Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:1000}")
	public void pollAndDispatch() {
		if (!dispatching.compareAndSet(false, true)) {
			return;
		}
		try {
			processingJobService.recoverStaleAndExpired();
			// Fill up to pool capacity; rejected tasks mean the bounded queue is full.
			for (int i = 0; i < 8; i++) {
				Optional<ProcessingJob> claimed = processingJobService.claimNext();
				if (claimed.isEmpty()) {
					break;
				}
				ProcessingJob job = claimed.get();
				try {
					documentJobExecutor.execute(() -> executeJob(job.getId(), job.getDocumentId(), job.getJobType()));
				}
				catch (RejectedExecutionException ex) {
					processingJobService.markFailedOrRetry(job.getId(), "WORKER_SATURATED");
					log.warn("Bounded executor rejected jobId={}", job.getId());
					break;
				}
			}
		}
		finally {
			dispatching.set(false);
		}
	}

	void executeJob(java.util.UUID jobId, java.util.UUID documentId, JobType jobType) {
		try {
			if (jobType == JobType.POST_PII_PIPELINE) {
				documentPipelineService.runPostPiiPipeline(documentId);
			}
			else {
				documentPipelineService.runInitialPipeline(documentId);
			}
			processingJobService.markSucceeded(jobId);
			log.info("Job succeeded jobId={} documentId={} type={}", jobId, documentId, jobType);
		}
		catch (BusinessException ex) {
			processingJobService.markFailedOrRetry(jobId, ex.getClass().getSimpleName());
			log.warn("Job business failure jobId={} documentId={} message={}", jobId, documentId, ex.getMessage());
		}
		catch (RuntimeException ex) {
			processingJobService.markFailedOrRetry(jobId, ex.getClass().getSimpleName());
			log.warn("Job runtime failure jobId={} documentId={} type={}", jobId, documentId, ex.getClass().getSimpleName());
		}
	}
}
