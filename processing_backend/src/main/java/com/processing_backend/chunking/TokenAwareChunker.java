package com.processing_backend.chunking;

import java.util.ArrayList;
import java.util.List;

import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import org.springframework.stereotype.Component;

/**
 * Configurable token-window chunker with overlap. Deterministic for the same input + settings.
 */
@Component
public class TokenAwareChunker {

	public List<TextChunk> chunk(ExtractedDocument document, int chunkSize, int overlap) {
		if (chunkSize < 1) {
			throw new IllegalArgumentException("chunkSize must be >= 1");
		}
		if (overlap < 0 || overlap >= chunkSize) {
			throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
		}
		if (document == null || document.pages() == null || document.pages().isEmpty()) {
			return List.of();
		}

		List<TokenSpan> tokens = new ArrayList<>();
		for (PdfPageContent page : document.pages()) {
			tokens.addAll(ApproximateTokenizer.tokenize(page.pageNumber(), page.text()));
		}
		if (tokens.isEmpty()) {
			return List.of();
		}

		List<TextChunk> chunks = new ArrayList<>();
		String previousContent = null;
		int index = 0;
		int start = 0;
		while (start < tokens.size()) {
			int end = Math.min(start + chunkSize, tokens.size());
			List<TokenSpan> window = tokens.subList(start, end);
			String content = joinTokens(window).trim();
			if (!content.isEmpty() && !content.equals(previousContent)) {
				int pageStart = window.get(0).pageNumber();
				int pageEnd = window.get(window.size() - 1).pageNumber();
				chunks.add(new TextChunk(index++, content, pageStart, pageEnd, window.size()));
				previousContent = content;
			}
			if (end >= tokens.size()) {
				break;
			}
			int next = end - overlap;
			if (next <= start) {
				next = start + 1;
			}
			start = next;
		}
		return List.copyOf(chunks);
	}

	/**
	 * Rebuild text from tokens: insert a single space between word-like tokens when needed.
	 */
	static String joinTokens(List<TokenSpan> tokens) {
		if (tokens.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		TokenSpan prev = null;
		for (TokenSpan token : tokens) {
			if (prev != null && needsSpace(prev, token)) {
				sb.append(' ');
			}
			sb.append(token.text());
			prev = token;
		}
		return sb.toString();
	}

	private static boolean needsSpace(TokenSpan left, TokenSpan right) {
		// Contiguous offsets on the same page → subword split of one original run; no space.
		if (left.pageNumber() == right.pageNumber()
				&& left.endExclusive() == right.startInclusive()) {
			return false;
		}
		char a = left.text().charAt(left.text().length() - 1);
		char b = right.text().charAt(0);
		if (isClosingPunct(b) || isOpeningPunct(a)) {
			return false;
		}
		if (isClosingPunct(a) || isOpeningPunct(b)) {
			return Character.isLetterOrDigit(b) || Character.isLetterOrDigit(a);
		}
		return true;
	}

	private static boolean isOpeningPunct(char c) {
		return c == '(' || c == '[' || c == '{' || c == '"' || c == '\'';
	}

	private static boolean isClosingPunct(char c) {
		return c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?'
				|| c == ')' || c == ']' || c == '}' || c == '"' || c == '\'';
	}
}
