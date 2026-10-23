package com.lending.platform.policyqa;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Component
public class OpenAiProvider implements EmbeddingProvider, ChatProvider {
    private final String apiKey;
    private final String baseUrl;
    private final String chatModel;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final RestClient client;

    public OpenAiProvider(@Value("${app.ai.api-key:}") String apiKey,
                          @Value("${app.ai.base-url}") String baseUrl,
                          @Value("${app.ai.chat-model}") String chatModel,
                          @Value("${app.ai.embedding-model}") String embeddingModel,
                          @Value("${app.ai.embedding-dimensions}") int embeddingDimensions,
                          @Value("${app.ai.timeout:20s}") Duration timeout) {
        if (embeddingDimensions != 1536) {
            throw new IllegalArgumentException("The selected text-embedding-3-small schema requires 1536 dimensions");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory).build();
    }

    @Override
    public List<Double> embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<List<Double>> embedAll(List<String> texts) {
        requireKey();
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            List<List<Double>> result = new ArrayList<>(texts.size());
            for (int offset = 0; offset < texts.size(); offset += 50) {
                List<String> batch = texts.subList(offset, Math.min(offset + 50, texts.size()));
                Map<String, Object> response = client.post().uri("/embeddings")
                        .body(Map.of("model", embeddingModel, "input", batch, "dimensions", embeddingDimensions))
                        .retrieve().body(Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
                List<Map<String, Object>> ordered = data.stream().sorted(Comparator.comparingInt(item -> ((Number) item.get("index")).intValue())).toList();
                if (ordered.size() != batch.size()) throw new AiProviderException("Embedding provider returned an incomplete batch");
                for (Map<String, Object> item : ordered) {
                    List<Number> raw = (List<Number>) item.get("embedding");
                    List<Double> vector = raw.stream().map(Number::doubleValue).toList();
                    if (vector.size() != embeddingDimensions || vector.stream().anyMatch(v -> !Double.isFinite(v)))
                        throw new AiProviderException("Embedding provider returned an invalid vector");
                    result.add(vector);
                }
            }
            return List.copyOf(result);
        } catch (RestClientException | ClassCastException | NullPointerException exception) {
            throw new AiProviderException("Embedding provider request failed", exception);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String answer(String systemPrompt, String userPrompt) {
        requireKey();
        try {
            Map<String, Object> response = client.post().uri("/chat/completions")
                    .body(Map.of("model", chatModel, "temperature", 0,
                            "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt))))
                    .retrieve().body(Map.class);
            Map<String, Object> choice = (Map<String, Object>) ((List<?>) response.get("choices")).get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            Object content = message.get("content");
            if (!(content instanceof String value) || value.isBlank()) {
                throw new AiProviderException("Chat provider returned an empty answer");
            }
            return value;
        } catch (RestClientException | ClassCastException | NullPointerException exception) {
            throw new AiProviderException("Chat provider request failed", exception);
        }
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) throw new AiProviderException("AI provider is not configured");
    }
}
