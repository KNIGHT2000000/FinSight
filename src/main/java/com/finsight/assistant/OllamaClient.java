package com.finsight.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * HTTP client for the local Ollama API.
 *
 * <p>Calls two endpoints:
 * <ul>
 *   <li>{@code POST /api/embeddings} — converts text to a dense float vector using
 *       {@code nomic-embed-text} (a small, fast 768-dim embedding model).
 *   <li>{@code POST /api/generate} — calls {@code llama3.1:8b} for text generation.
 *       Stream is disabled so the response arrives as a single JSON object.
 * </ul>
 *
 * <p>Model choice rationale:
 * <ul>
 *   <li>{@code llama3.1:8b} — 8B parameter quantised model that runs on consumer
 *       hardware (6–8 GB VRAM or 16 GB RAM with CPU offload). Reasonable instruction
 *       following for structured compliance-domain Q&amp;A.
 *   <li>{@code nomic-embed-text} — 137M parameter embedding model, ~274 MB on disk,
 *       produces high-quality 768-dim embeddings, much faster than running llama for
 *       embedding.
 * </ul>
 *
 * <p>All Ollama calls throw {@link OllamaUnavailableException} when Ollama is not
 * reachable, allowing the service layer to return a clean 503 instead of a 500.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient restClient;
    private final String     llmModel;
    private final String     embeddingModel;

    public OllamaClient(
            @Value("${finsight.assistant.ollama-base-url:http://localhost:11434}") String baseUrl,
            @Value("${finsight.assistant.llm-model:llama3.1:8b}")                  String llmModel,
            @Value("${finsight.assistant.embedding-model:nomic-embed-text}")        String embeddingModel) {
        this.llmModel       = llmModel;
        this.embeddingModel = embeddingModel;
        this.restClient     = RestClient.builder().baseUrl(baseUrl).build();
        log.info("OllamaClient configured: base={} llm={} embed={}", baseUrl, llmModel, embeddingModel);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Embed {@code text} using the configured embedding model.
     * Returns an empty array if Ollama is unreachable or returns no embedding.
     */
    public float[] embed(String text) {
        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingRequest(embeddingModel, text))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.embedding() == null) {
                log.warn("Ollama returned null embedding for text of length {}", text.length());
                return new float[0];
            }
            return toFloatArray(response.embedding());
        } catch (ResourceAccessException e) {
            throw new OllamaUnavailableException(
                "Ollama is not reachable at the configured base URL. " +
                "Run: ollama serve && ollama pull " + embeddingModel, e);
        }
    }

    /**
     * Send a prompt to the LLM and return the generated text.
     * Returns an empty string if Ollama is unreachable or response is null.
     */
    public String generate(String prompt) {
        try {
            GenerateResponse response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new GenerateRequest(llmModel, prompt, false))
                    .retrieve()
                    .body(GenerateResponse.class);

            if (response == null || response.response() == null) {
                log.warn("Ollama returned null generate response");
                return "";
            }
            return response.response();
        } catch (ResourceAccessException e) {
            throw new OllamaUnavailableException(
                "Ollama is not reachable. Run: ollama serve && ollama pull " + llmModel, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    // ── Request / Response DTOs (private — not part of public API) ────────────

    private record EmbeddingRequest(String model, String prompt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<Double> embedding) {}

    private record GenerateRequest(String model, String prompt, boolean stream) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateResponse(String model, String response, boolean done) {}

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class OllamaUnavailableException extends RuntimeException {
        public OllamaUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
