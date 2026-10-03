package io.github.vfedoriv.graphrag.schemas.discovery;

import io.github.vfedoriv.graphrag.schemas.discovery.adapters.model.CandidateExtractionModelAdapter;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourceAnalyzer;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionPromptFactory;
import io.github.vfedoriv.graphrag.schemas.discovery.CandidateExtractionResult;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts;
import io.github.vfedoriv.graphrag.schemas.discovery.PreparedDiscoverySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.TestAiObservationService;
import io.github.vfedoriv.graphrag.schemas.discovery.DiscoveryContracts.SourceType;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryRequest;
import io.github.vfedoriv.graphrag.ai.models.ProfileScopedAiClientResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class DiscoverySourceAnalyzerTest {

    @Test
    void invalidAttemptCandidatesAndAliasesDoNotLeakIntoSuccessfulRetry() {
        ProfileScopedAiClientResolver resolver = mock(ProfileScopedAiClientResolver.class);
        ChatModel model = mock(ChatModel.class);
        when(resolver.chatModel()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(
            response("""
                {"nodes":[{"label":null,"description":null,"confidence":0.5,"origin":"OBSERVED"}],
                 "nodeProperties":[],"nodeKeys":[],"relationships":[],"relationshipProperties":[],
                 "aliasSuggestions":[{"first":"Bad","second":"Leak","rationale":"private","confidence":0.5}]}
                """),
            response("""
                {"nodes":[{"label":"Person","description":null,"confidence":0.9,"origin":"OBSERVED"}],
                 "nodeProperties":[],"nodeKeys":[],"relationships":[],"relationshipProperties":[],
                 "aliasSuggestions":[{"first":"Human","second":"Person","rationale":"same","confidence":0.8}]}
                """)
        );
        CandidateExtractionModelAdapter adapter = new CandidateExtractionModelAdapter(
            resolver, TestAiObservationService.noop());
        DiscoverySourceAnalyzer analyzer = new DiscoverySourceAnalyzer(
            new CandidateExtractionPromptFactory(new ObjectMapper()), adapter);
        PreparedDiscoverySource source = new PreparedDiscoverySource(
            "source-1", SourceType.TEXT, "source", null, "fingerprint",
            List.of(new PreparedDiscoverySource.AnalysisChunk("chunk-1", 1, "text", "chunk-fingerprint")));
        SchemaDiscoveryRequest request = new SchemaDiscoveryRequest(
            List.of(), List.of(), null, SchemaDiscoveryRequest.DiscoveryGuidance.empty());

        DiscoverySourceAnalyzer.SourceAnalysis analysis = analyzer.analyze(source, request);

        assertThat(analysis.candidates()).extracting(DiscoveryContracts.Candidate::label).containsExactly("Person");
        assertThat(analysis.aliasSuggestions()).extracting(CandidateExtractionResult.AliasSuggestion::first)
            .containsExactly("Human");
        verify(model, times(2)).call(any(Prompt.class));
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
