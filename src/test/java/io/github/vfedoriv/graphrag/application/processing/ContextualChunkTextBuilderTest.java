package io.github.vfedoriv.graphrag.application.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.document.ParsedDocument;
import io.github.vfedoriv.graphrag.document.ParsedSection;
import io.github.vfedoriv.graphrag.document.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.document.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.document.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.document.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextualChunkTextBuilderTest {

    @Test
    void buildsStableBoundedEmbeddingTextWithoutChangingSourceText() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setOriginalFilename("contract.txt");
        ParsedSection section = new ParsedSection(
            0,
            "renewal terms",
            "text",
            "TXT",
            2,
            4,
            Map.of("language", "en")
        );
        ParsedDocument parsedDocument = new ParsedDocument("text", "TXT", List.of(section), Map.of());
        ChunkingContext context = ChunkingContext.create(
            RecursiveTokenAwareChunkingStrategy.NAME,
            RecursiveTokenAwareChunkingStrategy.REVISION,
            80,
            8,
            120,
            64,
            80,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "context-header-v1"
        );
        ChunkSlice slice = new RecursiveTokenAwareChunkingStrategy()
            .split(section, context)
            .getFirst();

        ContextualChunkTextBuilder.ContextualText result = new ContextualChunkTextBuilder()
            .build(document, parsedDocument, section, slice, context);

        assertThat(result.sourceText()).isEqualTo("renewal terms");
        assertThat(result.embeddingText())
            .startsWith("[context-header-v1]\n")
            .contains("source: contract.txt")
            .contains("format: TXT")
            .endsWith("renewal terms");
        assertThat(context.tokenEstimator().count(result.embeddingText())).isLessThanOrEqualTo(80);
        assertThat(result.embeddingText().length()).isLessThanOrEqualTo(120);
    }

    @Test
    void omitsUnavailableAndOverBudgetContextInsteadOfTruncatingSource() {
        ParsedSection section = new ParsedSection(0, "1234567890", null, null, null, null, Map.of());
        ParsedDocument parsedDocument = new ParsedDocument(null, null, List.of(section), Map.of());
        ChunkingContext context = ChunkingContext.create(
            RecursiveTokenAwareChunkingStrategy.NAME,
            RecursiveTokenAwareChunkingStrategy.REVISION,
            10,
            0,
            10,
            0,
            0,
            new Utf8ByteTokenEstimator(),
            "text-v1",
            "context-header-v1"
        );
        ChunkSlice slice = new RecursiveTokenAwareChunkingStrategy()
            .split(section, context)
            .getFirst();

        ContextualChunkTextBuilder.ContextualText result = new ContextualChunkTextBuilder()
            .build(new DocumentUploadNode(), parsedDocument, section, slice, context);

        assertThat(result.sourceText()).isEqualTo("1234567890");
        assertThat(result.embeddingText()).isEqualTo(result.sourceText());
        assertThat(result.contextualized()).isFalse();
    }
}
