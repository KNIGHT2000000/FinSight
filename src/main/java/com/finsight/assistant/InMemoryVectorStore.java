package com.finsight.assistant;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Thread-safe in-memory vector store backed by a simple ArrayList.
 *
 * <p>Retrieval uses cosine similarity between the query embedding and each stored
 * chunk embedding. This is O(n·d) where n=chunks and d=embedding dimension — perfectly
 * adequate for a corpus of O(100) document chunks. A production system would use
 * pgvector, Chroma, or Qdrant for O(log n) ANN search at scale.
 *
 * <p>The store is populated once at startup by {@link AssistantService} via
 * {@code @PostConstruct} and is read-only thereafter; no synchronization is needed
 * for the search path.
 */
@Component
public class InMemoryVectorStore {

    private final List<DocumentChunk> chunks = new ArrayList<>();

    /**
     * Add a chunk to the store. Called only during {@code @PostConstruct} initialization.
     */
    public void addChunk(DocumentChunk chunk) {
        chunks.add(chunk);
    }

    /**
     * Return the top-K chunks whose embeddings are most similar to the query embedding,
     * ordered by cosine similarity descending.
     *
     * @param queryEmbedding dense float vector produced by the embedding model
     * @param topK           maximum number of results to return
     * @return ordered list of the best matching document chunks
     */
    public List<DocumentChunk> search(float[] queryEmbedding, int topK) {
        if (chunks.isEmpty() || queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .map(ScoredChunk::chunk)
                .toList();
    }

    /** @return number of indexed document chunks */
    public int size() { return chunks.size(); }

    /** Clear the store — used in tests to reset state between runs. */
    public void clear() { chunks.clear(); }

    // ── Cosine similarity ─────────────────────────────────────────────────────

    /**
     * Compute cosine similarity between two float vectors.
     * Returns 0.0 when either vector has zero magnitude to avoid NaN.
     */
    static double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < len; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    /** Internal record pairing a chunk with its retrieval score. */
    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
