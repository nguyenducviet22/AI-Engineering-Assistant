package com.aiassistant.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenRouterEmbeddingLiveCheck {
    @Test
    void callsOpenRouterEmbeddingsEndpointForReal() throws Exception {
        String apiKey = apiKey();
        assertThat(apiKey)
                .as("OPENROUTER_API_KEY must be set in ../.env or process env for the live OpenRouter embeddings check")
                .isNotBlank();

        String body = """
                {"model":"openai/text-embedding-3-small","input":"live embedding verification"}
                """;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/embeddings"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("OpenRouter /embeddings HTTP status, body: " + response.body())
                .isBetween(200, 299);
        JsonNode root = new ObjectMapper().readTree(response.body());
        assertThat(root.path("data").isArray()).isTrue();
        int dimensions = root.path("data").get(0).path("embedding").size();
        System.out.printf("OpenRouter /embeddings live check: status=%d, dimensions=%d%n",
                response.statusCode(), dimensions);
        assertThat(dimensions).isEqualTo(1536);
    }

    private String apiKey() throws Exception {
        String fromEnv = System.getenv("OPENROUTER_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        Path envFile = Path.of("..", ".env");
        if (!Files.exists(envFile)) {
            return "";
        }
        Optional<String> line = Files.readAllLines(envFile).stream()
                .filter(value -> value.startsWith("OPENROUTER_API_KEY="))
                .findFirst();
        return line.map(value -> value.substring("OPENROUTER_API_KEY=".length()).trim()).orElse("");
    }
}
