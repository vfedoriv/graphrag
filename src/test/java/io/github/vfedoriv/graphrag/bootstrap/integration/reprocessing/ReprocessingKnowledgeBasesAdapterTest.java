package io.github.vfedoriv.graphrag.bootstrap.integration.reprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts;
import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingKnowledgeBases;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReprocessingKnowledgeBasesAdapterTest {
    @Test
    void preservesEveryNonSecretProfileIdentityAndManagedKnowledgeBaseFact() {
        SchemaWorkflowKnowledgeBaseFacts facts = mock(SchemaWorkflowKnowledgeBaseFacts.class);
        when(facts.activeProfile("kb")).thenReturn(new SchemaWorkflowKnowledgeBaseFacts.Profile(
            "profile", 7, "https://provider.test/v1", "embedding-model", 1536, "cl100k_base"));
        when(facts.requireManaged("kb")).thenReturn(new SchemaWorkflowKnowledgeBaseFacts.KnowledgeBase("kb", "schema"));
        when(facts.find("kb")).thenReturn(Optional.of(new SchemaWorkflowKnowledgeBaseFacts.KnowledgeBase("kb", "schema")));
        ReprocessingKnowledgeBasesAdapter adapter = new ReprocessingKnowledgeBasesAdapter(facts);
        assertThat(adapter.activeProfile("kb")).isEqualTo(new ReprocessingKnowledgeBases.Profile(
            "profile", 7, "https://provider.test/v1", "embedding-model", 1536, "cl100k_base"));
        assertThat(adapter.requireManaged("kb")).isEqualTo(new ReprocessingKnowledgeBases.KnowledgeBase("kb", "schema"));
        assertThat(adapter.find("kb")).contains(new ReprocessingKnowledgeBases.KnowledgeBase("kb", "schema"));
        assertThat(adapter.find("missing")).isEmpty();
        assertThat(ReprocessingKnowledgeBases.Profile.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("id", "revision", "baseUrl", "embeddingModel", "embeddingDimensions", "tokenizerId");
    }
}
