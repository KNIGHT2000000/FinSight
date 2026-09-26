package com.finsight.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads typology markdown documents from {@code classpath:typologies/*.md}
 * and splits each document into paragraph-level chunks for embedding.
 *
 * <p>Chunking strategy: split on double newline (blank line between paragraphs).
 * Chunks shorter than {@link #MIN_CHUNK_LENGTH} characters are discarded — they
 * are typically headings, source labels, or horizontal rules with no semantic content.
 *
 * <p>Each returned entry is a {@code String[2]}: {@code [filename, content]}.
 */
@Component
public class TypologyLoader {

    private static final Logger log = LoggerFactory.getLogger(TypologyLoader.class);

    /** Minimum character length for a chunk to be worth embedding. */
    private static final int MIN_CHUNK_LENGTH = 80;

    private final ResourcePatternResolver resourceResolver;

    public TypologyLoader(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * Load all markdown typology documents from the classpath and return
     * paragraph-level chunks as {@code [filename, chunkContent]} pairs.
     *
     * @return list of [filename, chunkText] pairs
     * @throws IOException if classpath scanning or file reading fails
     */
    public List<String[]> loadDocumentChunks() throws IOException {
        Resource[] resources = resourceResolver.getResources("classpath:typologies/*.md");
        log.info("Found {} typology documents on classpath", resources.length);

        List<String[]> chunks = new ArrayList<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            String fullText = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Split into paragraphs by one or more blank lines
            String[] paragraphs = fullText.split("\\n{2,}");

            int chunkCount = 0;
            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (trimmed.length() >= MIN_CHUNK_LENGTH) {
                    chunks.add(new String[]{ filename, trimmed });
                    chunkCount++;
                }
            }
            log.debug("Loaded {} chunks from {}", chunkCount, filename);
        }

        log.info("Total typology chunks loaded for embedding: {}", chunks.size());
        return chunks;
    }
}
