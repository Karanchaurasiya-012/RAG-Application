package com.processing_backend.embedding;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.processing_backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Resolves the requested provider and embeds text. Never silently switches providers.
 */
@Service
public class EmbeddingService {

	private final Map<EmbeddingProviderId, EmbeddingProvider> providers;

	public EmbeddingService(List<EmbeddingProvider> providerList) {
		Map<EmbeddingProviderId, EmbeddingProvider> map = new EnumMap<>(EmbeddingProviderId.class);
		for (EmbeddingProvider provider : providerList) {
			map.put(provider.id(), provider);
		}
		this.providers = Map.copyOf(map);
	}

	/** Document/chunk indexing embeddings ({@link EmbeddingTaskType#RETRIEVAL_DOCUMENT}). */
	public EmbeddingVector embed(String providerName, String text) {
		return embed(providerName, text, EmbeddingTaskType.RETRIEVAL_DOCUMENT);
	}

	/** Query embeddings ({@link EmbeddingTaskType#RETRIEVAL_QUERY}) for later retrieval APIs. */
	public EmbeddingVector embedQuery(String providerName, String text) {
		return embed(providerName, text, EmbeddingTaskType.RETRIEVAL_QUERY);
	}

	public EmbeddingVector embed(String providerName, String text, EmbeddingTaskType taskType) {
		EmbeddingProviderId id;
		try {
			id = EmbeddingProviderId.from(providerName);
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException("Unknown embedding provider: " + providerName, HttpStatus.BAD_REQUEST);
		}

		EmbeddingProvider provider = providers.get(id);
		if (provider == null) {
			throw new BusinessException(
					"Embedding provider is not available: " + id.name().toLowerCase(),
					HttpStatus.NOT_IMPLEMENTED
			);
		}
		return provider.embed(text, taskType);
	}
}
