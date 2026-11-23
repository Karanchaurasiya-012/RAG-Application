-- Local temp path for validated PDFs before S3 upload (Phase 9).
ALTER TABLE documents
    ADD COLUMN local_temp_path VARCHAR(1024);
