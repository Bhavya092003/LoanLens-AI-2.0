package com.lending.platform.policyqa;

import com.lending.platform.policydocument.PolicyDocumentPage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PolicyChunkingService {
    private final int chunkSize;
    private final int overlap;

    public PolicyChunkingService(@Value("${app.policy-retrieval.chunk-size:1200}") int chunkSize,
                                 @Value("${app.policy-retrieval.chunk-overlap:150}") int overlap) {
        if (chunkSize < 100 || overlap < 0 || overlap >= chunkSize) throw new IllegalArgumentException("Invalid policy chunk size/overlap");
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(List<PolicyDocumentPage> pages) {
        List<Chunk> result = new ArrayList<>();
        for (PolicyDocumentPage page : pages) {
            String text = page.getExtractedText() == null ? "" : page.getExtractedText().trim();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                if (end < text.length()) {
                    int boundary = Math.max(text.lastIndexOf(". ", end), Math.max(text.lastIndexOf("\n", end), text.lastIndexOf(" ", end)));
                    if (boundary > start + chunkSize / 2) end = boundary + 1;
                }
                String part = text.substring(start, end).trim();
                if (!part.isBlank()) result.add(new Chunk(result.size(), page.getPageNumber(), part));
                if (end >= text.length()) break;
                start = Math.max(start + 1, end - overlap);
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
            }
        }
        return List.copyOf(result);
    }

    public record Chunk(int index, int page, String text) {}
}
