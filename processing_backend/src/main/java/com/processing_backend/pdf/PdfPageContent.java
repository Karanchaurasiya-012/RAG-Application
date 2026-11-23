package com.processing_backend.pdf;

/**
 * One extracted PDF page. Page numbers are 1-based for citation compatibility.
 */
public record PdfPageContent(
		int pageNumber,
		String text
) {
}
