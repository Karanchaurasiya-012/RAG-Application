-- Phase 14: async job fields for bounded workers + delayed retry.
ALTER TABLE processing_jobs
    ADD COLUMN IF NOT EXISTS job_type VARCHAR(40) NOT NULL DEFAULT 'INITIAL_PIPELINE';

ALTER TABLE processing_jobs
    ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_processing_jobs_pending
    ON processing_jobs (status, next_run_at, created_at)
    WHERE status = 'PENDING';
