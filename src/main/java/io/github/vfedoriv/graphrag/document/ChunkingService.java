package io.github.vfedoriv.graphrag.document;

import io.github.vfedoriv.graphrag.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChunkingService {

    private final AppProperties appProperties;

    public ChunkingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public List<String> split(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            log.info("Chunking skipped: inputLength=0");
            return List.of();
        }
        int maxChars = appProperties.chunking().maxCharacters();
        int overlap = Math.min(appProperties.chunking().overlapTokens(), maxChars / 2);
        int step = Math.max(1, maxChars - overlap);

        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + maxChars);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
        }
        log.info(
            "Text chunked: inputLength={}, chunks={}, maxChars={}, overlap={}",
            normalized.length(),
            chunks.size(),
            maxChars,
            overlap
        );
        return chunks;
    }

    public int tokenEstimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }
}
