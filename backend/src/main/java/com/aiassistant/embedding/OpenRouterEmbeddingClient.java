package com.aiassistant.embedding;

import com.aiassistant.exception.ApiException;
import java.util.List;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OpenRouterEmbeddingClient implements EmbeddingClient {
    private final OpenRouterEmbeddingProperties properties;
    private final RestClient restClient;

    public OpenRouterEmbeddingClient(OpenRouterEmbeddingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.resolvedTimeout())
                .withReadTimeout(properties.resolvedTimeout());
        this.restClient = builder
                .baseUrl(properties.resolvedBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }

    @Override
    public EmbeddingVector embed(String content) {
        if (!properties.enabled()) {
            throw providerError("OpenRouter embedding is disabled.");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw providerError("OpenRouter API key is missing.");
        }
        EmbeddingRequest request = new EmbeddingRequest(properties.resolvedEmbeddingModel(), content);
        int attempts = Math.max(1, properties.maxRetries() + 1);
        RestClientException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                EmbeddingResponse response = restClient.post()
                        .uri("/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                        .body(request)
                        .retrieve()
                        .body(EmbeddingResponse.class);
                if (response == null || response.data() == null || response.data().isEmpty()) {
                    throw providerError("OpenRouter embedding response was empty.");
                }
                List<Double> values = response.data().getFirst().embedding();
                if (values == null || values.size() != 1536) {
                    throw providerError("OpenRouter embedding dimension mismatch. Expected 1536 dimensions.");
                }
                return new EmbeddingVector(properties.resolvedEmbeddingModel(), values.size(), values);
            } catch (RestClientException ex) {
                last = ex;
            }
        }
        throw providerError("OpenRouter embedding request failed: " + (last == null ? "unknown error" : last.getMessage()));
    }

    private ApiException providerError(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Embedding Service Unavailable", message);
    }

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(List<Double> embedding) {
    }
}
