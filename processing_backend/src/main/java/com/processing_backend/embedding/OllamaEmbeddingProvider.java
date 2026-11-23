package com.processing_backend.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.processing_backend.common.exception.BusinessException;
import com.processing_backend.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ollama {@code /api/embeddings} client for BGE-M3 (and compatible models).
 * Spring AI autoconfig is intentionally unused so provider selection stays explicit.
 */
@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

	private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);

	private final RestClient restClient;
	private final String model;
	private final int expectedDimensions;
	private final EmbeddingValidator embeddingValidator;

	public OllamaEmbeddingProvider(AppProperties appProperties, EmbeddingValidator embeddingValidator) {
		this.embeddingValidator = embeddingValidator;
		AppProperties.Embedding.Ollama ollama = appProperties.getEmbedding().getOllama();
		String baseUrl = ollama.getBaseUrl() == null || ollama.getBaseUrl().isBlank()
				? "http://localhost:11434"
				: ollama.getBaseUrl().replaceAll("/$", "");
		this.model = ollama.getModel() == null || ollama.getModel().isBlank() ? "bge-m3" : ollama.getModel();
		this.expectedDimensions = Math.max(1, appProperties.getEmbedding().getDimensions());

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofSeconds(120));
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
	}

	@Override
	public EmbeddingProviderId id() {
		return EmbeddingProviderId.OLLAMA;
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public EmbeddingVector embed(String text, EmbeddingTaskType taskType) {
		if (text == null || text.isBlank()) {
			throw new BusinessException("Cannot embed empty chunk text.", HttpStatus.UNPROCESSABLE_ENTITY);
		}
		// Ollama/BGE-M3 has no Gemini-style taskType; indexing and query use the same call.

		try {
			OllamaEmbeddingResponse response = restClient.post()
					.uri("/api/embeddings")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("model", model, "prompt", text))
					.retrieve()
					.body(OllamaEmbeddingResponse.class);

			if (response == null || response.embedding() == null) {
				throw new BusinessException("Ollama returned a malformed embedding response.", HttpStatus.BAD_GATEWAY);
			}

			float[] values = embeddingValidator.fromDoubles(response.embedding(), expectedDimensions);
			return new EmbeddingVector(values, id().name().toLowerCase(), model, values.length);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (RestClientResponseException ex) {
			log.warn("Ollama embedding HTTP error status={} model={}", ex.getStatusCode().value(), model);
			throw new BusinessException(
					"Ollama embedding request failed with HTTP " + ex.getStatusCode().value() + ".",
					HttpStatus.BAD_GATEWAY
			);
		}
		catch (RestClientException ex) {
			log.warn("Ollama embedding transport error type={} model={}", ex.getClass().getSimpleName(), model);
			throw new BusinessException(
					"Unable to reach Ollama embedding endpoint.",
					HttpStatus.BAD_GATEWAY
			);
		}
	}

	public record OllamaEmbeddingResponse(List<Double> embedding) {
	}
}
