package com.processing_backend.job;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

	Optional<ProcessingJob> findFirstByDocumentIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
			UUID documentId,
			JobType jobType,
			List<JobStatus> statuses
	);

	@Query(value = """
			UPDATE processing_jobs
			SET status = 'RUNNING',
			    attempts = attempts + 1,
			    started_at = NOW(),
			    updated_at = NOW(),
			    last_error_code = NULL
			WHERE id = (
			    SELECT id FROM processing_jobs
			    WHERE status = 'PENDING'
			      AND (next_run_at IS NULL OR next_run_at <= NOW())
			    ORDER BY created_at ASC
			    FOR UPDATE SKIP LOCKED
			    LIMIT 1
			)
			RETURNING id
			""", nativeQuery = true)
	List<UUID> claimNextJobId();

	@Modifying
	@Query("""
			UPDATE ProcessingJob j
			SET j.status = com.processing_backend.job.JobStatus.PENDING,
			    j.nextRunAt = :nextRunAt,
			    j.updatedAt = :now
			WHERE j.status = com.processing_backend.job.JobStatus.RUNNING
			  AND j.startedAt < :staleBefore
			  AND j.attempts < :maxAttempts
			""")
	int requeueStaleRunning(
			@Param("staleBefore") Instant staleBefore,
			@Param("maxAttempts") int maxAttempts,
			@Param("nextRunAt") Instant nextRunAt,
			@Param("now") Instant now
	);

	@Modifying
	@Query("""
			UPDATE ProcessingJob j
			SET j.status = com.processing_backend.job.JobStatus.FAILED,
			    j.lastErrorCode = 'STALE_RUNNING',
			    j.completedAt = :now,
			    j.updatedAt = :now
			WHERE j.status = com.processing_backend.job.JobStatus.RUNNING
			  AND j.startedAt < :staleBefore
			  AND j.attempts >= :maxAttempts
			""")
	int failStaleRunning(
			@Param("staleBefore") Instant staleBefore,
			@Param("maxAttempts") int maxAttempts,
			@Param("now") Instant now
	);

	@Modifying
	@Query("""
			UPDATE ProcessingJob j
			SET j.status = com.processing_backend.job.JobStatus.CANCELLED,
			    j.completedAt = :now,
			    j.updatedAt = :now,
			    j.lastErrorCode = 'EXPIRED'
			WHERE j.status IN (
			    com.processing_backend.job.JobStatus.PENDING,
			    com.processing_backend.job.JobStatus.RUNNING
			)
			  AND j.createdAt < :expiryBefore
			""")
	int cancelExpired(@Param("expiryBefore") Instant expiryBefore, @Param("now") Instant now);

	Optional<ProcessingJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

	@Modifying(clearAutomatically = true)
	@Query("""
			UPDATE ProcessingJob j
			SET j.status = com.processing_backend.job.JobStatus.CANCELLED,
			    j.completedAt = :now,
			    j.updatedAt = :now,
			    j.lastErrorCode = 'DOCUMENT_DELETED'
			WHERE j.documentId = :documentId
			  AND j.status IN (
			      com.processing_backend.job.JobStatus.PENDING,
			      com.processing_backend.job.JobStatus.RUNNING
			  )
			""")
	int cancelActiveByDocumentId(@Param("documentId") UUID documentId, @Param("now") Instant now);

	void deleteByDocumentId(UUID documentId);
}
