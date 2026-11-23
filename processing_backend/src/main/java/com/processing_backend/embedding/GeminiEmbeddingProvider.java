package com.processing_backend.embedding;

import java.time.Duration;
import java.util.LinkedHashMap;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google Generative Language {@code embedContent} client for {@code gemini-embedding-001}.
 * Uses {@link EmbeddingTaskType#RETRIEVAL_DOCUMENT} for indexing and
 * {@link EmbeddingTaskType#RETRIEVAL_QUERY} for queries. Requests {@code outputDimensionality}
 * matching configured VECTOR(1024) and L2-normalizes truncated vectors per Gemini docs.
 */
@Component
public class GeminiEmbeddingProvider implements EmbeddingProvider {

	private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingProvider.class);
	private static final String BASE_URL = "https://generativelanguage.googleapis.com";
	private static final int NATIVE_DIMENSIONS = 3072;

	private final RestClient restClient;
	private final String model;
	private final String apiKey;
	private final int expectedDimensions;
	private final EmbeddingValidator embeddingValidator;

	public GeminiEmbeddingProvider(AppProperties appProperties, EmbeddingValidator embeddingValidator) {
		this.embeddingValidator = embeddingValidator;
		AppProperties.Embedding.Gemini gemini = appProperties.getEmbedding().getGemini();
		this.model = gemini.getModel() == null || gemini.getModel().isBlank()
				? "gemini-embedding-001"
				: gemini.getModel().trim();
		this.apiKey = gemini.getApiKey() == null ? "" : gemini.getApiKey().trim();
		this.expectedDimensions = Math.max(1, appProperties.getEmbedding().getDimensions());

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofSeconds(120));
		this.restClient = RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(requestFactory)
				.build();
	}

	@Override
	public EmbeddingProviderId id() {
		return EmbeddingProviderId.GEMINI;
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
		if (apiKey.isBlank()) {
			throw new BusinessException(
					"GEMINI_API_KEY is not configured for the Gemini embedding provider.",
					HttpStatus.SERVICE_UNAVAILABLE
			);
		}
		EmbeddingTaskType resolved = taskType == null ? EmbeddingTaskType.RETRIEVAL_DOCUMENT : taskType;

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", "models/" + model);
		body.put("content", Map.of("parts", List.of(Map.of("text", text))));
		body.put("taskType", resolved.name());
		body.put("outputDimensionality", expectedDimensions);

		String uri = UriComponentsBuilder
				.fromPath("/v1beta/models/{model}:embedContent")
				.queryParam("key", apiKey)
				.buildAndExpand(model)
				.toUriString();

		try {
			GeminiEmbedResponse response = restClient.post()
					.uri(uri)
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(GeminiEmbedResponse.class);

			if (response == null || response.embedding() == null || response.embedding().values() == null) {
				throw new BusinessException("Gemini returned a malformed embedding response.", HttpStatus.BAD_GATEWAY);
			}

			float[] values = embeddingValidator.fromDoubles(response.embedding().values(), expectedDimensions);
			if (expectedDimensions != NATIVE_DIMENSIONS) {
				values = VectorMath.l2Normalize(values);
				values = embeddingValidator.requireValid(values, expectedDimensions);
			}
			return new EmbeddingVector(values, id().name().toLowerCase(), model, values.length);
		}
		catch (BusinessException ex) {
			throw ex;
		}
		catch (RestClientResponseException ex) {
			log.warn("Gemini embedding HTTP error status={} model={} taskType={}",
					ex.getStatusCode().value(), model, resolved);
			throw new BusinessException(
					"Gemini embedding request failed with HTTP " + ex.getStatusCode().value() + ".",
					HttpStatus.BAD_GATEWAY
			);
		}
		catch (RestClientException ex) {
			log.warn("Gemini embedding transport error type={} model={}", ex.getClass().getSimpleName(), model);
			throw new BusinessException(
					"Unable to reach Gemini embedding endpoint.",
					HttpStatus.BAD_GATEWAY
			);
		}
	}

	public record GeminiEmbedResponse(Embedding embedding) {
		public record Embedding(List<Double> values) {
		}
	}
}
