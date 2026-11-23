package com.processing_backend.chunking;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic token estimator that does <em>not</em> treat character length as token count.
 * <p>
 * Heuristic (stable across runs):
 * <ul>
 *   <li>Whitespace is a separator (not a token).</li>
 *   <li>Each punctuation / symbol code point is its own token.</li>
 *   <li>CJK / other non-Latin letters are one token per code point.</li>
 *   <li>Latin/digit runs are one token up to {@link #MAX_WORD_TOKEN_LEN} chars;
 *       longer runs are split into subword pieces of {@link #SUBWORD_LEN}.</li>
 * </ul>
 * This approximates subword tokenizers used by embedding models without bundling a model vocab.
 */
public final class ApproximateTokenizer {

	static final int MAX_WORD_TOKEN_LEN = 12;
	static final int SUBWORD_LEN = 4;

	private ApproximateTokenizer() {
	}

	public static List<TokenSpan> tokenize(int pageNumber, String text) {
		List<TokenSpan> tokens = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return tokens;
		}

		int i = 0;
		final int n = text.length();
		while (i < n) {
			int cp = text.codePointAt(i);
			int charCount = Character.charCount(cp);

			if (Character.isWhitespace(cp)) {
				i += charCount;
				continue;
			}

			if (isWordCodePoint(cp)) {
				int start = i;
				int end = i + charCount;
				while (end < n) {
					int next = text.codePointAt(end);
					if (!isWordCodePoint(next)) {
						break;
					}
					end += Character.charCount(next);
				}
				emitWordRun(pageNumber, text, start, end, tokens);
				i = end;
				continue;
			}

			tokens.add(new TokenSpan(pageNumber, i, i + charCount, text.substring(i, i + charCount)));
			i += charCount;
		}
		return tokens;
	}

	private static void emitWordRun(int pageNumber, String text, int start, int end, List<TokenSpan> out) {
		int len = end - start;
		if (len <= MAX_WORD_TOKEN_LEN) {
			out.add(new TokenSpan(pageNumber, start, end, text.substring(start, end)));
			return;
		}
		int cursor = start;
		while (cursor < end) {
			int pieceEnd = Math.min(cursor + SUBWORD_LEN, end);
			// avoid splitting a surrogate pair mid-piece
			if (pieceEnd < end && Character.isHighSurrogate(text.charAt(pieceEnd - 1))) {
				pieceEnd++;
			}
			out.add(new TokenSpan(pageNumber, cursor, pieceEnd, text.substring(cursor, pieceEnd)));
			cursor = pieceEnd;
		}
	}

	private static boolean isWordCodePoint(int cp) {
		if (Character.isLetterOrDigit(cp) || cp == '_') {
			// CJK / Hangul / Hiragana / Katakana → per-code-point tokens (handled by caller as non-word)
			Character.UnicodeScript script = Character.UnicodeScript.of(cp);
			return script != Character.UnicodeScript.HAN
					&& script != Character.UnicodeScript.HIRAGANA
					&& script != Character.UnicodeScript.KATAKANA
					&& script != Character.UnicodeScript.HANGUL;
		}
		return false;
	}

	public static int countTokens(String text) {
		return tokenize(1, text == null ? "" : text).size();
	}
}
