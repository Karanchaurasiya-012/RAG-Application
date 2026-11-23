package com.processing_backend.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.processing_backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

	@Test
	void usesRequestedProviderWithoutFallback() {
		RecordingProvider ollama = new RecordingProvider(EmbeddingProviderId.OLLAMA, "bge-m3:latest");
		RecordingProvider gemini = new RecordingProvider(EmbeddingProviderId.GEMINI, "gemini-embedding-001");

		EmbeddingService service = new EmbeddingService(List.of(ollama, gemini));
		EmbeddingVector vector = service.embed("gemini", "hello");

		assertThat(vector.provider()).isEqualTo("gemini");
		assertThat(gemini.lastTask.get()).isEqualTo(EmbeddingTaskType.RETRIEVAL_DOCUMENT);
		assertThat(ollama.calls).isEmpty();
	}

	@Test
	void embedQueryUsesRetrievalQueryTaskType() {
		RecordingProvider gemini = new RecordingProvider(EmbeddingProviderId.GEMINI, "gemini-embedding-001");
		EmbeddingService service = new EmbeddingService(List.of(gemini));

		service.embedQuery("gemini", "what is rag?");
		assertThat(gemini.lastTask.get()).isEqualTo(EmbeddingTaskType.RETRIEVAL_QUERY);
	}

	@Test
	void failsClearlyWhenProviderMissing() {
		EmbeddingService service = new EmbeddingService(List.of());
		assertThatThrownBy(() -> service.embed("ollama", "hello"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("not available");
	}

	private static final class RecordingProvider implements EmbeddingProvider {
		private final EmbeddingProviderId id;
		private final String model;
		private final List<String> calls = new ArrayList<>();
		private final AtomicReference<EmbeddingTaskType> lastTask = new AtomicReference<>();

		private RecordingProvider(EmbeddingProviderId id, String model) {
			this.id = id;
			this.model = model;
		}

		@Override
		public EmbeddingProviderId id() {
			return id;
		}

		@Override
		public String model() {
			return model;
		}

		@Override
		public EmbeddingVector embed(String text, EmbeddingTaskType taskType) {
			calls.add(text);
			lastTask.set(taskType);
			return new EmbeddingVector(new float[]{0.1f, 0.2f}, id.name().toLowerCase(), model, 2);
		}
	}
}
