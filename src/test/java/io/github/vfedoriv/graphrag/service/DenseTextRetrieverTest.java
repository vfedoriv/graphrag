package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.repository.TextChunkRetrievalRepository.RawCandidate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DenseTextRetrieverTest {

    @Test
    void batchEmbedsSubqueriesOnceAndPreservesSubqueryRanks() {
        AtomicReference<List<String>> embeddedTexts = new AtomicReference<>();
        EmbeddingClient embeddingClient = texts -> {
            embeddedTexts.set(texts);
            return List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));
        };
        Fixture fixture = fixture(embeddingClient);
        when(fixture.repository.findDense(eq("vector-index"), eq("kb-1"), eq("space-1"), anyList(), eq(2)))
            .thenReturn(List.of(raw("chunk-1", 0.9), raw("chunk-2", 0.8)));

        io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result result = fixture.retriever.retrieve(
            request(List.of(new Subquery("q-1", "first"), new Subquery("q-2", "second")))
        );

        assertThat(embeddedTexts.get()).containsExactly("first", "second");
        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).extracting(candidate -> candidate.subqueryId())
            .containsExactly("q-1", "q-1", "q-2", "q-2");
        assertThat(result.candidates()).extracting(candidate -> candidate.rank())
            .containsExactly(1, 2, 1, 2);
        verify(fixture.repository, org.mockito.Mockito.times(2)).findDense(
            eq("vector-index"), eq("kb-1"), eq("space-1"), anyList(), eq(2)
        );
    }

    @Test
    void reportsOnlySanitizedFailureCategory() {
        Fixture fixture = fixture(texts -> {
            throw new IllegalArgumentException("private query material");
        });

        io.github.vfedoriv.graphrag.domain.AdvancedSearchTextRetrievalContracts.Result result = fixture.retriever.retrieve(
            request(List.of(new Subquery("q-1", "secret")))
        );

        assertThat(result.diagnostics().status()).isEqualTo(Status.FAILED);
        assertThat(result.diagnostics().failureCategory()).isEqualTo("IllegalArgumentException");
        assertThat(result.toString()).doesNotContain("private query material", "secret");
    }

    private Fixture fixture(EmbeddingClient embeddingClient) {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        when(resolver.embeddingClient()).thenReturn(embeddingClient);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile());
        EmbeddingSpacePolicy policy = mock(EmbeddingSpacePolicy.class);
        when(policy.hasEmbeddedChunks("kb-1")).thenReturn(true);
        when(policy.spaceFor(any(AiProfileNode.class))).thenReturn(space());
        EmbeddingSpaceIndexService indexService = mock(EmbeddingSpaceIndexService.class);
        when(indexService.indexName("kb-1", "space-1")).thenReturn("vector-index");
        TextChunkRetrievalRepository repository = mock(TextChunkRetrievalRepository.class);
        DenseTextRetriever retriever = new DenseTextRetriever(
            resolver, knowledgeBaseService, policy, indexService, repository
        );
        return new Fixture(retriever, repository);
    }

    private Request request(List<Subquery> subqueries) {
        return new Request(
            "kb-1", subqueries, new MetadataConstraints(null, null), 2, true, Instant.now().plusSeconds(10)
        );
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        return profile;
    }

    private EmbeddingSpace space() {
        return new EmbeddingSpace("space-1", "https://example.test/v1", "embedding", 2, "tokenizer");
    }

    private RawCandidate raw(String chunkId, double score) {
        return new RawCandidate(
            new SourceIdentity(chunkId, "doc-1", 0, 0, 10, null, null, "run-1", "rev-1", "section"),
            score,
            "source only"
        );
    }

    private record Fixture(DenseTextRetriever retriever, TextChunkRetrievalRepository repository) { }
}
