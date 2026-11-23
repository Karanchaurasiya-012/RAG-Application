package com.processing_backend.job;

public enum JobType {
	/** UPLOADED → extract → PII gate or full completion. */
	INITIAL_PIPELINE,
	/** After user PII decision → prepare → S3 → chunk → embed → complete. */
	POST_PII_PIPELINE
}
