package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.search.ranking.adapters.model.AdvancedSearchReranker;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchDiversitySelector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.EvidenceSource;
import io.github.vfedoriv.graphrag.search.ranking.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.infrastructure.ai.ProfileScopedAiClientResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class AdvancedSearchRankingComponentsTest {

    @Test
    void invalidRerankerOutputFallsBackToExactFusedOrder() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        when(resolver.chatModel()).thenReturn(chatModel("{\"ranking\":[{\"candidateId\":\"unknown\",\"score\":1.0}]}"));
        AdvancedSearchReranker reranker = new AdvancedSearchReranker(resolver);
        List<EvidenceCandidate> candidates = List.of(
            candidate("c-1", "doc-1", 1),
            candidate("c-2", "doc-2", 2)
        );

        AdvancedSearchReranker.RerankResult result = reranker.rerank("query", candidates, 20);

        assertThat(result.diagnostics().fallbackUsed()).isTrue();
        assertThat(result.candidates()).extracting(EvidenceCandidate::chunkId).containsExactly("c-1", "c-2");
    }

    @Test
    void validStructuredScoresRerankOnlyTheBoundedPool() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        when(resolver.chatModel()).thenReturn(chatModel("""
            {"ranking":[
              {"candidateId":"c-1","score":0.1},
              {"candidateId":"c-2","score":0.9}
            ]}
            """));
        AdvancedSearchReranker reranker = new AdvancedSearchReranker(resolver);

        AdvancedSearchReranker.RerankResult result = reranker.rerank(
            "query",
            List.of(candidate("c-1", "doc-1", 1), candidate("c-2", "doc-2", 2), candidate("c-3", "doc-3", 3)),
            2
        );

        assertThat(result.diagnostics().fallbackUsed()).isFalse();
        assertThat(result.candidates()).extracting(EvidenceCandidate::chunkId).containsExactly("c-2", "c-1", "c-3");
    }

    @Test
    void diversityDefersExcessSameDocumentCandidates() {
        AdvancedSearchDiversitySelector selector = new AdvancedSearchDiversitySelector();
        List<EvidenceCandidate> candidates = List.of(
            candidate("c-1", "doc-1", 1),
            candidate("c-2", "doc-1", 2),
            candidate("c-3", "doc-1", 3),
            candidate("c-4", "doc-2", 4)
        );

        AdvancedSearchDiversitySelector.SelectionResult result = selector.select(candidates, 3, 2, false);

        assertThat(result.candidates()).extracting(EvidenceCandidate::chunkId).containsExactly("c-1", "c-2", "c-4");
        assertThat(result.diagnostics().skippedForDiversity()).isEqualTo(1);
    }

    private ChatModel chatModel(String response) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
            }
        };
    }

    private EvidenceCandidate candidate(String chunkId, String documentId, int rank) {
        return new EvidenceCandidate(
            new EvidenceSource(
                chunkId,
                documentId,
                rank,
                new SourceBounds(rank * 10, rank * 10 + 5, rank, rank),
                "run-1",
                "revision-1",
                "section"
            ),
            CitationKind.TEXT_CHILD,
            "candidate text " + chunkId,
            List.of(),
            List.of(),
            null,
            1.0 / (60 + rank),
            rank,
            null
        );
    }
}
