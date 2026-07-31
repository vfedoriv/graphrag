package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.repository.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository.RawCandidate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalTextRetrieverTest {

    @Test
    void returnsUnprefixedSourceTextAndRawRank() {
        LexicalIndexRepository indexes = indexRepository();
        TextChunkRetrievalRepository chunks = mock(TextChunkRetrievalRepository.class);
        when(chunks.findLexical("lexical-index", "kb-1", "context", 2)).thenReturn(List.of(
            new RawCandidate(source(), 4.2, "source body without contextual embedding header")
        ));
        LexicalTextRetriever retriever = new LexicalTextRetriever(indexes, chunks, new LuceneQueryCompiler());

        io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result result = retriever.retrieve(
            request("context")
        );

        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.text()).isEqualTo("source body without contextual embedding header");
            assertThat(candidate.rank()).isEqualTo(1);
            assertThat(candidate.rawScore()).isEqualTo(4.2);
        });
    }

    @Test
    void reportsIndexFailureWithoutLeakingItsMessage() {
        LexicalIndexRepository indexes = indexRepository();
        org.mockito.Mockito.doThrow(new IllegalStateException("private index detail"))
            .when(indexes).ensureOnline(org.mockito.ArgumentMatchers.eq("kb-1"), org.mockito.ArgumentMatchers.any());
        LexicalTextRetriever retriever = new LexicalTextRetriever(
            indexes, mock(TextChunkRetrievalRepository.class), new LuceneQueryCompiler()
        );

        io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result result = retriever.retrieve(
            request("source")
        );

        assertThat(result.diagnostics().status()).isEqualTo(Status.FAILED);
        assertThat(result.diagnostics().failureCategory()).isEqualTo("IllegalStateException");
        assertThat(result.toString()).doesNotContain("private index detail");
    }

    private LexicalIndexRepository indexRepository() {
        LexicalIndexRepository indexes = mock(LexicalIndexRepository.class);
        when(indexes.indexName("kb-1")).thenReturn("lexical-index");
        return indexes;
    }

    private Request request(String text) {
        return new Request(
            "kb-1",
            List.of(new Subquery("q-1", text)),
            new MetadataConstraints(null, null),
            2,
            true,
            Instant.now().plusSeconds(10)
        );
    }

    private SourceIdentity source() {
        return new SourceIdentity("chunk-1", "doc-1", 0, 0, 20, 1, 1, "run-1", "rev-1", "Section");
    }
}
