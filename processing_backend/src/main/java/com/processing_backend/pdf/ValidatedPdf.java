package com.processing_backend.pdf;

public record ValidatedPdf(
		byte[] bytes,
		String sanitizedFilename,
		String contentType,
		String checksumSha256,
		int pageCount
) {
}
