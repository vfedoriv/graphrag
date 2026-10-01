package io.github.vfedoriv.graphrag.documents.domain.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedDocument;
import io.github.vfedoriv.graphrag.documents.domain.parsing.ParsedSection;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkSlice;
import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkingContext;
import io.github.vfedoriv.graphrag.documents.domain.chunking.RecursiveTokenAwareChunkingStrategy;
import io.github.vfedoriv.graphrag.documents.domain.chunking.Utf8ByteTokenEstimator;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
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
        ChunkSlice slice = new RecursiveTokenAwareChunkingStrategy((text, referenceContext) ->
            new io.github.vfedoriv.graphrag.documents.adapters.chunking.LangChain4jRecursiveSplitterAdapter(referenceContext)
                .referenceSegmentCount(text))
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
        ChunkSlice slice = new RecursiveTokenAwareChunkingStrategy((text, referenceContext) ->
            new io.github.vfedoriv.graphrag.documents.adapters.chunking.LangChain4jRecursiveSplitterAdapter(referenceContext)
                .referenceSegmentCount(text))
            .split(section, context)
            .getFirst();

        ContextualChunkTextBuilder.ContextualText result = new ContextualChunkTextBuilder()
            .build(new DocumentUploadNode(), parsedDocument, section, slice, context);

        assertThat(result.sourceText()).isEqualTo("1234567890");
        assertThat(result.embeddingText()).isEqualTo(result.sourceText());
        assertThat(result.contextualized()).isFalse();
    }
}
