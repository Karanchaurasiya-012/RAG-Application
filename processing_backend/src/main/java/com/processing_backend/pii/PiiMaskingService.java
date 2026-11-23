package com.processing_backend.pii;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PiiMaskingService {

	/**
	 * Applies category placeholders using original offsets, right-to-left per page.
	 * Does not log raw matched values.
	 */
	public ExtractedDocument mask(ExtractedDocument source, List<PiiSpan> spans) {
		if (source == null) {
			throw new BusinessException("Extracted text is required for masking.", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		if (spans == null || spans.isEmpty()) {
			return source;
		}

		List<PiiSpan> resolved = PiiDetectionService.resolveOverlaps(spans);
		List<PdfPageContent> maskedPages = new ArrayList<>(source.pages().size());
		int nonEmpty = 0;
		int totalChars = 0;

		for (PdfPageContent page : source.pages()) {
			String masked = maskPage(page, resolved);
			verifyAbsent(page, masked, resolved);
			maskedPages.add(new PdfPageContent(page.pageNumber(), masked));
			if (!masked.isBlank()) {
				nonEmpty++;
			}
			totalChars += masked.length();
		}

		return new ExtractedDocument(List.copyOf(maskedPages), maskedPages.size(), nonEmpty, totalChars);
	}

	private static String maskPage(PdfPageContent page, List<PiiSpan> allSpans) {
		List<PiiSpan> pageSpans = allSpans.stream()
				.filter(span -> span.pageNumber() == page.pageNumber())
				.sorted(Comparator.comparingInt(PiiSpan::startOffset).reversed())
				.toList();

		StringBuilder text = new StringBuilder(page.text());
		for (PiiSpan span : pageSpans) {
			if (span.startOffset() < 0 || span.endOffset() > text.length() || span.startOffset() >= span.endOffset()) {
				throw new BusinessException(
						"Unsafe PII offset for masking on page " + page.pageNumber() + ".",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			String placeholder = "[" + span.entityType().name() + "]";
			text.replace(span.startOffset(), span.endOffset(), placeholder);
		}
		return text.toString();
	}

	private static void verifyAbsent(PdfPageContent originalPage, String masked, List<PiiSpan> allSpans) {
		String original = originalPage.text();
		for (PiiSpan span : allSpans) {
			if (span.pageNumber() != originalPage.pageNumber()) {
				continue;
			}
			if (span.startOffset() < 0 || span.endOffset() > original.length()) {
				throw new BusinessException(
						"Cannot verify masking due to invalid offset.",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
			String raw = original.substring(span.startOffset(), span.endOffset());
			if (!raw.isBlank() && masked.contains(raw)) {
				throw new BusinessException(
						"Masking verification failed: detected value still present in sanitized text.",
						HttpStatus.UNPROCESSABLE_ENTITY
				);
			}
		}
	}
}
