package com.aiassistant.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.aiassistant.exception.ApiException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenRouterEmbeddingClientTests {

    @Test
    void providerAuthFailureReturnsSafeUnavailableErrorWithoutRawProviderDetails() {
        OpenRouterEmbeddingProperties properties = new OpenRouterEmbeddingProperties(
                "https://openrouter.ai/api/v1",
                "invalid-test-key",
                "openai/text-embedding-3-small",
                Duration.ofSeconds(2),
                0,
                true);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.resolvedBaseUrl());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterEmbeddingClient client = new OpenRouterEmbeddingClient(properties, builder.build());
        server.expect(requestTo("https://openrouter.ai/api/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Throwable thrown = catchThrowable(() -> client.embed("How does authentication work?"));

        assertThat(thrown).isInstanceOf(ApiException.class);
        ApiException apiException = (ApiException) thrown;
        assertThat(apiException.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(apiException.error()).isEqualTo("AI Provider Unavailable");
        assertThat(apiException.getMessage()).isEqualTo("The assistant is temporarily unavailable, please try again.");
        assertThat(apiException.getMessage()).doesNotContain("OpenRouter", "401", "Unauthorized", "invalid-test-key");
        server.verify();
    }
}
