package com.processing_backend.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.processing_backend.pdf.ExtractedDocument;
import com.processing_backend.pdf.PdfPageContent;
import org.junit.jupiter.api.Test;

class TokenAwareChunkerTest {

	private final TokenAwareChunker chunker = new TokenAwareChunker();

	@Test
	void producesDeterministicChunksWithOverlapAndPageMetadata() {
		String page1 = IntStream.rangeClosed(1, 40)
				.mapToObj(i -> "word" + i)
				.collect(Collectors.joining(" "));
		String page2 = IntStream.rangeClosed(41, 80)
				.mapToObj(i -> "word" + i)
				.collect(Collectors.joining(" "));

		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(1, page1), new PdfPageContent(2, page2)),
				2, 2, page1.length() + page2.length()
		);

		List<TextChunk> first = chunker.chunk(doc, 25, 5);
		List<TextChunk> second = chunker.chunk(doc, 25, 5);

		assertThat(first).isNotEmpty();
		assertThat(first).isEqualTo(second);
		assertThat(first.get(0).chunkIndex()).isZero();
		assertThat(first.get(0).pageStart()).isEqualTo(1);
		assertThat(first.get(first.size() - 1).pageEnd()).isEqualTo(2);
		assertThat(first).allMatch(c -> c.tokenCount() > 0 && !c.content().isBlank());
		assertThat(first.stream().map(TextChunk::chunkIndex).distinct().count()).isEqualTo(first.size());
	}

	@Test
	void skipsEmptyDocuments() {
		ExtractedDocument empty = new ExtractedDocument(
				List.of(new PdfPageContent(1, "   \n")),
				1, 0, 0
		);
		assertThat(chunker.chunk(empty, 600, 80)).isEmpty();
	}

	@Test
	void rejectsInvalidOverlap() {
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(1, "a b c")),
				1, 1, 5
		);
		assertThatThrownBy(() -> chunker.chunk(doc, 10, 10))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void respectsChunkSizeCeiling() {
		List<String> words = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			words.add("tok" + i);
		}
		String text = String.join(" ", words);
		ExtractedDocument doc = new ExtractedDocument(
				List.of(new PdfPageContent(1, text)),
				1, 1, text.length()
		);
		List<TextChunk> chunks = chunker.chunk(doc, 20, 4);
		assertThat(chunks).isNotEmpty();
		assertThat(chunks.subList(0, chunks.size() - 1))
				.allMatch(c -> c.tokenCount() <= 20);
	}
}
