package org.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TextSplitter {

    // A simple chunking limit. Can be adjusted.
    private static final int MAX_CHUNK_SIZE = 512;
    // Split by two or more newline characters.
    private static final Pattern PARAGRAPH_SPLITTER = Pattern.compile("\\n\\s*\\n");

    /**
     * Splits a given text into smaller chunks suitable for embedding.
     * <p>
     * The strategy is as follows:
     * 1. Split the text into paragraphs based on double newlines.
     * 2. For each paragraph, if it's within the MAX_CHUNK_SIZE, treat it as a chunk.
     * 3. If a paragraph is too long, split it into smaller pieces of at most MAX_CHUNK_SIZE.
     *
     * @param text The input text to split.
     * @return A list of text chunks.
     */
    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> initialChunks = List.of(PARAGRAPH_SPLITTER.split(text));
        List<String> finalChunks = new ArrayList<>();

        for (String chunk : initialChunks) {
            if (chunk.isBlank()) {
                continue;
            }
            if (chunk.length() <= MAX_CHUNK_SIZE) {
                finalChunks.add(chunk.trim());
            } else {
                // The chunk is too long, so we split it by size.
                int start = 0;
                while (start < chunk.length()) {
                    int end = Math.min(chunk.length(), start + MAX_CHUNK_SIZE);
                    finalChunks.add(chunk.substring(start, end).trim());
                    start = end;
                }
            }
        }
        return finalChunks;
    }
}
