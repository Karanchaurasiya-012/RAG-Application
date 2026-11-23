package com.processing_backend.pdf;

import java.util.List;

public record ExtractedDocument(
		List<PdfPageContent> pages,
		int pageCount,
		int nonEmptyPageCount,
		int totalCharacterCount
) {
}
