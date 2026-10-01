package io.github.vfedoriv.graphrag.documents.domain.processing;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import java.util.ArrayList;
import java.util.List;

public final class ContextualChunkTextBuilder {

    public ContextualText build(
        DocumentUploadNode document,
        ParsedDocument parsedDocument,
        ParsedSection section,
        ChunkSlice slice,
        ChunkingContext context
    ) {
        String sourceText = slice.text();
        int availableTokens = Math.min(
            context.contextHeaderMaxTokens(),
            Math.max(0, context.targetTokens() - context.tokenEstimator().count(sourceText))
        );
        int availableCharacters = Math.min(
            context.contextHeaderMaxCharacters(),
            Math.max(0, context.hardCharacterLimit() - sourceText.length())
        );
        if (availableTokens == 0 || availableCharacters == 0) {
            return new ContextualText(sourceText, sourceText, 0, false);
        }

        List<String> fields = new ArrayList<>();
        add(fields, "source", document == null ? null : document.getOriginalFilename());
        add(fields, "format", section.format() == null ? parsedDocument.format() : section.format());
        if (!slice.structuralPath().isEmpty()) {
            add(fields, "path", String.join(" > ", slice.structuralPath()));
        }
        if (slice.pageStart() != null) {
            String page = slice.pageEnd().equals(slice.pageStart())
                ? String.valueOf(slice.pageStart())
                : slice.pageStart() + "-" + slice.pageEnd();
            if (section.pageCount() != null) {
                page += "/" + section.pageCount();
            }
            add(fields, "page", page);
        }

        List<String> accepted = new ArrayList<>();
        for (String field : fields) {
            List<String> prospective = new ArrayList<>(accepted);
            prospective.add(field);
            String header = header(context.representationRevision(), prospective);
            if (header.length() <= availableCharacters
                && context.tokenEstimator().count(header) <= availableTokens) {
                accepted = prospective;
            }
        }
        if (accepted.isEmpty()) {
            return new ContextualText(sourceText, sourceText, 0, false);
        }
        String header = header(context.representationRevision(), accepted);
        String embeddingText = header + sourceText;
        if (embeddingText.length() > context.hardCharacterLimit()
            || context.tokenEstimator().count(embeddingText) > context.targetTokens()) {
            return new ContextualText(sourceText, sourceText, 0, false);
        }
        return new ContextualText(
            sourceText,
            embeddingText,
            context.tokenEstimator().count(header),
            true
        );
    }

    private void add(List<String> fields, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.replaceAll("\\s+", " ").strip();
        if (normalized.length() > 160) {
            normalized = normalized.substring(0, 160);
        }
        fields.add(name + ": " + normalized);
    }

    private String header(String revision, List<String> fields) {
        return "[" + revision + "]\n" + String.join("\n", fields) + "\n\n";
    }

    public record ContextualText(
        String sourceText,
        String embeddingText,
        int headerTokenCount,
        boolean contextualized
    ) {
    }
}
