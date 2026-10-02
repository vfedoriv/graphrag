package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.ai.domain.StoredEmbeddingObservation;
import io.github.vfedoriv.graphrag.ai.ports.StoredEmbeddingInformation;
import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import io.github.vfedoriv.graphrag.search.retrieval.adapters.model.DenseEmbeddingAdapter;
import io.github.vfedoriv.graphrag.search.retrieval.application.DenseTextRetriever;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.MetadataConstraints;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Branch;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Request;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.SourceIdentity;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Status;
import io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Subquery;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository;
import io.github.vfedoriv.graphrag.search.retrieval.ports.TextChunkRetrievalRepository.RawCandidate;
import io.github.vfedoriv.graphrag.search.retrieval.ports.SearchEmbeddingModel;
import io.github.vfedoriv.graphrag.search.runs.adapters.model.SearchProfileAdapter;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles;
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
        when(fixture.repository.findDense(
            eq("vector-index"), eq("kb-1"), eq(fixture.target.id()), anyList(), eq(2)
        )).thenReturn(List.of(raw("chunk-1", 0.9), raw("chunk-2", 0.8)));

        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result result =
            fixture.retriever.retrieve(request(List.of(new Subquery("q-1", "first"), new Subquery("q-2", "second"))));

        assertThat(embeddedTexts.get()).containsExactly("first", "second");
        assertThat(result.diagnostics().status()).isEqualTo(Status.COMPLETED);
        assertThat(result.candidates()).extracting(candidate -> candidate.branch())
            .containsOnly(Branch.DENSE);
        assertThat(result.candidates()).extracting(candidate -> candidate.subqueryId())
            .containsExactly("q-1", "q-1", "q-2", "q-2");
        assertThat(result.candidates()).extracting(candidate -> candidate.rank())
            .containsExactly(1, 2, 1, 2);
        verify(fixture.repository, org.mockito.Mockito.times(2)).findDense(
            eq("vector-index"), eq("kb-1"), eq(fixture.target.id()), anyList(), eq(2)
        );
    }

    @Test
    void reportsOnlySanitizedFailureCategory() {
        Fixture fixture = fixture(texts -> {
            throw new IllegalArgumentException("private query material");
        });

        io.github.vfedoriv.graphrag.search.retrieval.domain.AdvancedSearchTextRetrievalContracts.Result result =
            fixture.retriever.retrieve(request(List.of(new Subquery("q-1", "secret"))));

        assertThat(result.diagnostics().status()).isEqualTo(Status.FAILED);
        assertThat(result.diagnostics().failureCategory()).isEqualTo("IllegalArgumentException");
        assertThat(result.toString()).doesNotContain("private query material", "secret");
    }

    private Fixture fixture(EmbeddingClient embeddingClient) {
        AiProfileNode profile = profile();
        AiProfileService profileService = mock(AiProfileService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        AiRuntimeModelFactory runtimeModelFactory = mock(AiRuntimeModelFactory.class);
        when(knowledgeBaseService.activeAiProfile("kb-1")).thenReturn(profile);
        SearchProfiles profiles = new SearchProfileAdapter(profileService, knowledgeBaseService, runtimeModelFactory);

        EmbeddingTarget target = EmbeddingTarget.derive(
            profile.getBaseUrl(), profile.getEmbeddingModel(), profile.getEmbeddingDimensions(), profile.getTokenizerIdValue()
        );
        StoredEmbeddingInformation storedEmbeddings = mock(StoredEmbeddingInformation.class);
        when(storedEmbeddings.observations("kb-1")).thenReturn(List.of(
            new StoredEmbeddingObservation(target.id(), target.model(), target.tokenizerId())
        ));
        EmbeddingCompatibility compatibility = new EmbeddingCompatibility(storedEmbeddings);

        ProfileScopedAiClientResolver clientResolver = mock(ProfileScopedAiClientResolver.class);
        when(clientResolver.embeddingClient()).thenReturn(embeddingClient);
        SearchEmbeddingModel embeddings = new DenseEmbeddingAdapter(profiles, compatibility, clientResolver);

        EmbeddingSpaceIndexService indexService = mock(EmbeddingSpaceIndexService.class);
        when(indexService.indexName("kb-1", target.id())).thenReturn("vector-index");
        TextChunkRetrievalRepository repository = mock(TextChunkRetrievalRepository.class);
        DenseTextRetriever retriever = new DenseTextRetriever(embeddings, compatibility, indexService, repository);
        return new Fixture(retriever, repository, target);
    }

    private Request request(List<Subquery> subqueries) {
        return new Request(
            "kb-1", subqueries, new MetadataConstraints(null, null), 2, true, Instant.now().plusSeconds(10)
        );
    }

    private AiProfileNode profile() {
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");
        profile.setRevision(4);
        profile.setBaseUrl("https://example.test/v1");
        profile.setEmbeddingModel("embedding");
        profile.setEmbeddingDimensions(2);
        return profile;
    }

    private RawCandidate raw(String chunkId, double score) {
        return new RawCandidate(
            new SourceIdentity(chunkId, "doc-1", 0, 0, 10, null, null, "run-1", "rev-1", "section"),
            score,
            "source only"
        );
    }

    private record Fixture(DenseTextRetriever retriever, TextChunkRetrievalRepository repository, EmbeddingTarget target) { }
}
