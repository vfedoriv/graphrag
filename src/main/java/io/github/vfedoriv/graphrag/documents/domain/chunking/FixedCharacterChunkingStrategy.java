package io.github.vfedoriv.graphrag.documents.domain.chunking;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FixedCharacterChunkingStrategy implements ChunkingStrategy {

    public static final String NAME = "fixed-character";
    public static final String REVISION = "fixed-character-v1";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String revision() {
        return REVISION;
    }

    @Override
    public List<ChunkSlice> split(ParsedSection section, ChunkingContext context) {
        String source = section == null || section.text() == null ? "" : section.text();
        String normalized = source.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }
        int normalizedStart = source.indexOf(normalized);
        int maxCharacters = context.hardCharacterLimit();
        int overlapCharacters = Math.min(context.overlapTokens(), maxCharacters / 2);
        int step = Math.max(1, maxCharacters - overlapCharacters);
        List<ChunkSlice> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length(); start += step) {
            int end = Math.min(normalized.length(), start + maxCharacters);
            String text = normalized.substring(start, end);
            chunks.add(new ChunkSlice(
                text,
                normalizedStart + start,
                normalizedStart + end,
                context.tokenEstimator().count(text),
                context.strategyName(),
                context.strategyRevision(),
                context.tokenEstimator().tokenizerId(),
                context.tokenEstimator().countMode(),
                context.settingsHash(),
                context.effectiveRevision(),
                "CHILD",
                section.sectionIndex(),
                chunks.size(),
                section.pageNumber(),
                section.pageNumber(),
                List.of(),
                null,
                ChunkHashes.sha256(text),
                Map.of("overlapCharacters", overlapCharacters)
            ));
            if (end == normalized.length()) {
                break;
            }
        }
        return List.copyOf(chunks);
    }
}
