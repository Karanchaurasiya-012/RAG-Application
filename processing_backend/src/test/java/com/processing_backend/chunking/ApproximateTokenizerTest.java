package com.processing_backend.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ApproximateTokenizerTest {

	@Test
	void doesNotEquateCharacterCountWithTokenCount() {
		String text = "hello world!!!";
		List<TokenSpan> tokens = ApproximateTokenizer.tokenize(1, text);
		assertThat(tokens).hasSizeGreaterThan(2);
		assertThat(tokens.size()).isNotEqualTo(text.length());
		assertThat(ApproximateTokenizer.countTokens(text)).isEqualTo(tokens.size());
	}

	@Test
	void splitsLongWordsIntoSubwordPieces() {
		String longWord = "abcdefghijklmnopqrstuvwxyz";
		List<TokenSpan> tokens = ApproximateTokenizer.tokenize(1, longWord);
		assertThat(tokens.size()).isGreaterThan(1);
		assertThat(tokens.stream().map(TokenSpan::text).reduce("", String::concat)).isEqualTo(longWord);
	}

	@Test
	void treatsCjkAsPerCodePointTokens() {
		List<TokenSpan> tokens = ApproximateTokenizer.tokenize(1, "你好");
		assertThat(tokens).hasSize(2);
	}

	@Test
	void skipsWhitespaceOnly() {
		assertThat(ApproximateTokenizer.tokenize(1, "   \n\t  ")).isEmpty();
	}
}
