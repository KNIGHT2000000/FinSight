package com.finsight.assistant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.finsight.assistant.InMemoryVectorStore.cosineSimilarity;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the retrieval plumbing — specifically the InMemoryVectorStore's
 * cosine similarity search. Tests are fully hermetic (no Spring context, no Ollama).
 *
 * <p>Design: each typology document is assigned an orthogonal unit vector as its
 * embedding. Query embeddings are then designed to be closest to one specific document.
 * This makes the expected retrieval order mathematically deterministic and verifiable.
 *
 * <p>What these tests DO verify:
 * <ul>
 *   <li>Cosine similarity math is correct (dot product / norms)
 *   <li>Top-K ordering returns documents in similarity-descending order
 *   <li>The correct source document is returned for a query closest to it
 * </ul>
 *
 * <p>What these tests do NOT verify:
 * <ul>
 *   <li>LLM response quality (untestable without an evaluation harness)
 *   <li>Actual Ollama embedding quality (requires a running Ollama instance)
 * </ul>
 */
class AssistantServiceTest {

    private InMemoryVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();

        // Each document gets a unique orthogonal unit-vector embedding.
        // Dimension 0 = wash_trading, 1 = layering, 2 = front_running,
        //              3 = collusion_rings, 4 = cross_market
        vectorStore.addChunk(new DocumentChunk(
            "c1", "01_fatf_wash_trading.md",
            "Wash trading involves buying and selling the same securities to create false market activity.",
            new float[]{ 1.0f, 0.0f, 0.0f, 0.0f, 0.0f }
        ));
        vectorStore.addChunk(new DocumentChunk(
            "c2", "02_fincen_layering_spoofing.md",
            "Layering involves placing large orders to move prices then cancelling before execution.",
            new float[]{ 0.0f, 1.0f, 0.0f, 0.0f, 0.0f }
        ));
        vectorStore.addChunk(new DocumentChunk(
            "c3", "03_sec_front_running.md",
            "Front-running is trading ahead of a pending customer block order.",
            new float[]{ 0.0f, 0.0f, 1.0f, 0.0f, 0.0f }
        ));
        vectorStore.addChunk(new DocumentChunk(
            "c4", "04_fatf_collusion_rings.md",
            "Collusion rings involve coordinated trading across multiple accounts.",
            new float[]{ 0.0f, 0.0f, 0.0f, 1.0f, 0.0f }
        ));
        vectorStore.addChunk(new DocumentChunk(
            "c5", "05_finra_cross_market_manipulation.md",
            "Cross-market manipulation uses one market to artificially move prices in another.",
            new float[]{ 0.0f, 0.0f, 0.0f, 0.0f, 1.0f }
        ));
    }

    @Test
    @DisplayName("Retrieval: query closest to wash-trading document returns 01_fatf_wash_trading.md as top result")
    void testRetrieval_WashTradingQuery_ReturnsCorrectSourceDocument() {
        // Query embedding is close to wash_trading (dim 0) with minor noise in other dims
        float[] query = new float[]{ 0.95f, 0.05f, 0.01f, 0.01f, 0.01f };

        List<DocumentChunk> results = vectorStore.search(query, 1);

        assertFalse(results.isEmpty(), "Search must return at least one result");
        assertEquals("01_fatf_wash_trading.md", results.get(0).getSource(),
            "Query most similar to wash-trading embedding must retrieve the wash-trading document");
    }

    @Test
    @DisplayName("Retrieval: query about collusion rings returns 04_fatf_collusion_rings.md as top result")
    void testRetrieval_CollusionRingQuery_ReturnsCorrectSourceDocument() {
        float[] query = new float[]{ 0.02f, 0.03f, 0.01f, 0.98f, 0.02f };

        List<DocumentChunk> results = vectorStore.search(query, 1);

        assertFalse(results.isEmpty());
        assertEquals("04_fatf_collusion_rings.md", results.get(0).getSource(),
            "Query most similar to collusion-ring embedding must retrieve the collusion-ring document");
    }

    @Test
    @DisplayName("Retrieval: top-3 search returns results ordered by cosine similarity descending")
    void testTopKRetrieval_ResultsOrderedByCosineSimilarity() {
        // Query is closest to layering (dim 1), second-closest to front_running (dim 2)
        float[] query = new float[]{ 0.01f, 0.90f, 0.40f, 0.05f, 0.02f };

        List<DocumentChunk> results = vectorStore.search(query, 3);

        assertEquals(3, results.size(), "Should return exactly 3 results");
        assertEquals("02_fincen_layering_spoofing.md", results.get(0).getSource(),
            "First result must be the layering document (highest cosine similarity)");
        assertEquals("03_sec_front_running.md", results.get(1).getSource(),
            "Second result must be front-running document");
    }

    @Test
    @DisplayName("Cosine similarity: identical vectors yield similarity of exactly 1.0")
    void testCosineSimilarity_IdenticalVectors_ReturnsOne() {
        float[] v = { 0.5f, 0.5f, 0.5f, 0.5f, 0.5f };
        double similarity = cosineSimilarity(v, v);
        assertEquals(1.0, similarity, 1e-6, "Identical vectors must have cosine similarity of 1.0");
    }

    @Test
    @DisplayName("Cosine similarity: orthogonal vectors yield similarity of 0.0")
    void testCosineSimilarity_OrthogonalVectors_ReturnsZero() {
        float[] a = { 1.0f, 0.0f, 0.0f };
        float[] b = { 0.0f, 1.0f, 0.0f };
        double similarity = cosineSimilarity(a, b);
        assertEquals(0.0, similarity, 1e-6, "Orthogonal vectors must have cosine similarity of 0.0");
    }

    @Test
    @DisplayName("Cosine similarity: zero-magnitude vector returns 0.0 (no NaN)")
    void testCosineSimilarity_ZeroVector_ReturnsZeroNot_NaN() {
        float[] zero = { 0.0f, 0.0f, 0.0f };
        float[] v    = { 1.0f, 0.5f, 0.2f };
        double result = cosineSimilarity(zero, v);
        assertFalse(Double.isNaN(result), "Zero vector must not produce NaN");
        assertEquals(0.0, result, 1e-6);
    }

    @Test
    @DisplayName("Search: empty store returns empty list (no NullPointerException)")
    void testSearch_EmptyStore_ReturnsEmptyList() {
        InMemoryVectorStore emptyStore = new InMemoryVectorStore();
        List<DocumentChunk> results = emptyStore.search(new float[]{ 1.0f, 0.0f }, 3);
        assertNotNull(results);
        assertTrue(results.isEmpty(), "Empty store must return empty list");
    }

    @Test
    @DisplayName("Search: topK larger than store size returns all available chunks")
    void testSearch_TopKLargerThanStoreSize_ReturnsAllChunks() {
        float[] query = new float[]{ 0.2f, 0.2f, 0.2f, 0.2f, 0.2f };
        List<DocumentChunk> results = vectorStore.search(query, 100); // store has 5
        assertEquals(5, results.size(), "Should return all 5 chunks when topK exceeds store size");
    }

    @Test
    @DisplayName("Store size: 5 chunks correctly indexed")
    void testVectorStoreSize() {
        assertEquals(5, vectorStore.size(), "Vector store must contain exactly 5 indexed chunks");
    }
}
