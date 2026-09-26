package com.finsight.assistant;

/**
 * A single chunk of a typology document with its vector embedding.
 * Immutable value class — stored in InMemoryVectorStore and retrieved via cosine similarity.
 */
public class DocumentChunk {

    private final String id;
    private final String source;    // filename, e.g. "01_fatf_wash_trading.md"
    private final String content;   // raw text of this paragraph chunk
    private final float[] embedding; // dense vector from Ollama nomic-embed-text

    public DocumentChunk(String id, String source, String content, float[] embedding) {
        this.id        = id;
        this.source    = source;
        this.content   = content;
        this.embedding = embedding;
    }

    public String  getId()        { return id; }
    public String  getSource()    { return source; }
    public String  getContent()   { return content; }
    public float[] getEmbedding() { return embedding; }
}
