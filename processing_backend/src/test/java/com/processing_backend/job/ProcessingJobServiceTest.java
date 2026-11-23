package com.processing_backend.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.processing_backend.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

class ProcessingJobServiceTest {

	private InMemoryProcessingJobRepository repository;
	private ProcessingJobService processingJobService;

	@BeforeEach
	void setUp() {
		repository = new InMemoryProcessingJobRepository();
		AppProperties props = new AppProperties();
		props.getJobs().setMaxAttempts(3);
		props.getJobs().setRetryDelayMs(1000);
		processingJobService = new ProcessingJobService(repository, props);
	}

	@Test
	void enqueueIsIdempotentWhenActiveJobExists() {
		UUID documentId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		ProcessingJob first = processingJobService.enqueue(documentId, ownerId, JobType.INITIAL_PIPELINE);
		ProcessingJob second = processingJobService.enqueue(documentId, ownerId, JobType.INITIAL_PIPELINE);
		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(repository.store).hasSize(1);
	}

	@Test
	void retriesUntilMaxAttemptsThenFails() {
		ProcessingJob job = processingJobService.enqueue(UUID.randomUUID(), UUID.randomUUID(), JobType.INITIAL_PIPELINE);
		job.setAttempts(2);
		job.setStatus(JobStatus.RUNNING);
		repository.save(job);

		processingJobService.markFailedOrRetry(job.getId(), "EMBEDDING_FAILED");
		ProcessingJob updated = repository.findById(job.getId()).orElseThrow();
		assertThat(updated.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(updated.getLastErrorCode()).isEqualTo("EMBEDDING_FAILED");

		updated.setAttempts(3);
		updated.setStatus(JobStatus.RUNNING);
		repository.save(updated);
		processingJobService.markFailedOrRetry(job.getId(), "EMBEDDING_FAILED");
		assertThat(repository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.FAILED);
	}

	/**
	 * Minimal repository stub — avoids Mockito inline agent requirements in this environment.
	 */
	static final class InMemoryProcessingJobRepository implements ProcessingJobRepository {
		private final ConcurrentMap<UUID, ProcessingJob> store = new ConcurrentHashMap<>();

		@Override
		public Optional<ProcessingJob> findFirstByDocumentIdAndJobTypeAndStatusInOrderByCreatedAtDesc(
				UUID documentId, JobType jobType, List<JobStatus> statuses
		) {
			return store.values().stream()
					.filter(j -> documentId.equals(j.getDocumentId()))
					.filter(j -> jobType == j.getJobType())
					.filter(j -> statuses.contains(j.getStatus()))
					.findFirst();
		}

		@Override
		public List<UUID> claimNextJobId() {
			return List.of();
		}

		@Override
		public int requeueStaleRunning(java.time.Instant staleBefore, int maxAttempts, java.time.Instant nextRunAt, java.time.Instant now) {
			return 0;
		}

		@Override
		public int failStaleRunning(java.time.Instant staleBefore, int maxAttempts, java.time.Instant now) {
			return 0;
		}

		@Override
		public int cancelExpired(java.time.Instant expiryBefore, java.time.Instant now) {
			return 0;
		}

		@Override
		public Optional<ProcessingJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId) {
			return store.values().stream()
					.filter(j -> documentId.equals(j.getDocumentId()))
					.findFirst();
		}

		@Override
		public int cancelActiveByDocumentId(UUID documentId, java.time.Instant now) {
			return 0;
		}

		@Override
		public void deleteByDocumentId(UUID documentId) {
			store.entrySet().removeIf(e -> documentId.equals(e.getValue().getDocumentId()));
		}

		@Override
		public ProcessingJob save(ProcessingJob entity) {
			if (entity.getId() == null) {
				entity.setId(UUID.randomUUID());
			}
			store.put(entity.getId(), entity);
			return entity;
		}

		@Override
		public Optional<ProcessingJob> findById(UUID id) {
			return Optional.ofNullable(store.get(id));
		}

		@Override
		public <S extends ProcessingJob> List<S> saveAll(Iterable<S> entities) {
			List<S> out = new ArrayList<>();
			entities.forEach(e -> out.add((S) save(e)));
			return out;
		}

		@Override
		public boolean existsById(UUID id) {
			return store.containsKey(id);
		}

		@Override
		public List<ProcessingJob> findAll() {
			return new ArrayList<>(store.values());
		}

		@Override
		public List<ProcessingJob> findAllById(Iterable<UUID> ids) {
			List<ProcessingJob> out = new ArrayList<>();
			ids.forEach(id -> findById(id).ifPresent(out::add));
			return out;
		}

		@Override
		public long count() {
			return store.size();
		}

		@Override
		public void deleteById(UUID id) {
			store.remove(id);
		}

		@Override
		public void delete(ProcessingJob entity) {
			if (entity.getId() != null) {
				store.remove(entity.getId());
			}
		}

		@Override
		public void deleteAllById(Iterable<? extends UUID> ids) {
			ids.forEach(this::deleteById);
		}

		@Override
		public void deleteAll(Iterable<? extends ProcessingJob> entities) {
			entities.forEach(this::delete);
		}

		@Override
		public void deleteAll() {
			store.clear();
		}

		@Override
		public List<ProcessingJob> findAll(Sort sort) {
			return findAll();
		}

		@Override
		public Page<ProcessingJob> findAll(Pageable pageable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S extends ProcessingJob> Optional<S> findOne(Example<S> example) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S extends ProcessingJob> List<S> findAll(Example<S> example) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S extends ProcessingJob> List<S> findAll(Example<S> example, Sort sort) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S extends ProcessingJob> Page<S> findAll(Example<S> example, Pageable pageable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <S extends ProcessingJob> long count(Example<S> example) {
			return 0;
		}

		@Override
		public <S extends ProcessingJob> boolean exists(Example<S> example) {
			return false;
		}

		@Override
		public <S extends ProcessingJob, R> R findBy(Example<S> example, java.util.function.Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() {
		}

		@Override
		public <S extends ProcessingJob> S saveAndFlush(S entity) {
			return (S) save(entity);
		}

		@Override
		public <S extends ProcessingJob> List<S> saveAllAndFlush(Iterable<S> entities) {
			return saveAll(entities);
		}

		@Override
		public void deleteAllInBatch(Iterable<ProcessingJob> entities) {
			deleteAll(entities);
		}

		@Override
		public void deleteAllByIdInBatch(Iterable<UUID> ids) {
			deleteAllById(ids);
		}

		@Override
		public void deleteAllInBatch() {
			deleteAll();
		}

		@Override
		public ProcessingJob getOne(UUID id) {
			return findById(id).orElseThrow();
		}

		@Override
		public ProcessingJob getById(UUID id) {
			return findById(id).orElseThrow();
		}

		@Override
		public ProcessingJob getReferenceById(UUID id) {
			return findById(id).orElseThrow();
		}
	}
}
