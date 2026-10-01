package io.github.vfedoriv.graphrag.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.ai.application.EmbeddingCompatibility;
import io.github.vfedoriv.graphrag.ai.domain.*;
import io.github.vfedoriv.graphrag.bootstrap.integration.ai.*;
import io.github.vfedoriv.graphrag.bootstrap.integration.knowledgebase.KnowledgeBaseDocumentsAdapter;
import io.github.vfedoriv.graphrag.documents.application.inspection.StoredEmbeddingsFacade;
import io.github.vfedoriv.graphrag.documents.application.lifecycle.KnowledgeBaseDocumentsFacade;
import io.github.vfedoriv.graphrag.documents.contracts.*;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.error.EmbeddingSpaceConflictException;
import io.github.vfedoriv.graphrag.knowledgebase.application.AiProfileAssignmentsFacade;
import io.github.vfedoriv.graphrag.repository.*;
import io.github.vfedoriv.graphrag.service.GraphArtifactCleanupService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiStateBoundariesTest {
    @Test
    void preservesHistoricalIdentityAndKeepsTokenizerSeparate() {
        EmbeddingTarget target = EmbeddingTarget.derive("HTTPS://API.OPENAI.COM:443/v1/", " text-embedding-3-small ", 1536, null);
        assertThat(target.normalizedBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(target.id()).isEqualTo("es_104ba9ed8a07d2429402424e49946895a4c0575d77276293a9f2957d1268902d");
        EmbeddingTarget implicit = EmbeddingTarget.derive("https://model.example/v1", "alias", 768, null);
        EmbeddingTarget explicit = EmbeddingTarget.derive("https://model.example/v1", "alias", 768, "cl100k_base");
        assertThat(explicit.id()).isEqualTo(implicit.id());
        assertThat(explicit.tokenizerId()).isEqualTo("cl100k_base");
        assertThat(implicit.tokenizerId()).isEqualTo("utf8-byte-v1");
    }

    @Test
    void rejectsChangedProviderModelDimensionsTokenizerAndMissingSpaceWithoutBackfill() {
        EmbeddingTarget target = EmbeddingTarget.derive("https://api.openai.com/v1", "text-embedding-3-small", 1536, null);
        assertThat(EmbeddingCompatibilityRule.compatible(target, List.of())).isTrue();
        for (String tokenizer : new String[] {null, "", "  ", "cl100k_base"}) {
            assertThat(EmbeddingCompatibilityRule.compatible(target, List.of(
                new StoredEmbeddingObservation(target.id(), "text-embedding-3-small", tokenizer)))).isTrue();
        }
        assertThat(EmbeddingCompatibilityRule.compatible(target, List.of(
            new StoredEmbeddingObservation(null, "text-embedding-3-small", null)))).isFalse();
        assertThat(EmbeddingCompatibilityRule.compatible(target, List.of(
            new StoredEmbeddingObservation("", "text-embedding-3-small", null)))).isFalse();
        assertThat(EmbeddingCompatibilityRule.compatible(target, List.of(
            new StoredEmbeddingObservation(target.id(), "text-embedding-3-small", "utf8-byte-v1")))).isFalse();
        List<StoredEmbeddingObservation> stored = List.of(new StoredEmbeddingObservation(target.id(), target.model(), null));
        assertThat(EmbeddingCompatibilityRule.compatible(EmbeddingTarget.derive("https://another.example/v1", target.model(), 1536, null), stored)).isFalse();
        assertThat(EmbeddingCompatibilityRule.compatible(EmbeddingTarget.derive("https://api.openai.com/v1", "other", 1536, null), stored)).isFalse();
        assertThat(EmbeddingCompatibilityRule.compatible(EmbeddingTarget.derive("https://api.openai.com/v1", target.model(), 768, null), stored)).isFalse();
        EmbeddingTarget unknown = EmbeddingTarget.derive("https://model.example/v1", "alias", 768, null);
        assertThat(EmbeddingCompatibilityRule.compatible(unknown, List.of(new StoredEmbeddingObservation(unknown.id(), "alias", " ")))).isTrue();
        assertThat(EmbeddingCompatibilityRule.compatible(unknown, List.of(new StoredEmbeddingObservation(unknown.id(), null, null)))).isTrue();
    }

    @Test
    void storedInspectionMapsRawFieldsAndUsesOnlyEmbeddedKnowledgeBaseScope() {
        DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
        StoredEmbeddingInformationAdapter adapter = new StoredEmbeddingInformationAdapter(new StoredEmbeddingsFacade(chunks));
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setEmbeddingSpaceId(null);
        chunk.setEmbeddingModel(" model ");
        chunk.setTokenizerId(" ");
        when(chunks.findEmbeddedChunksByKnowledgeBaseId("owned")).thenReturn(List.of(chunk));
        assertThat(adapter.observations("owned")).containsExactly(new StoredEmbeddingObservation(null, " model ", " "));
        when(chunks.findEmbeddedChunksByKnowledgeBaseId("empty")).thenReturn(null);
        assertThat(adapter.observations("empty")).isEmpty();
        verify(chunks).findEmbeddedChunksByKnowledgeBaseId("owned");
        verify(chunks).findEmbeddedChunksByKnowledgeBaseId("empty");
        verifyNoMoreInteractions(chunks);
        RuntimeException failure = new IllegalStateException("inspection failed");
        when(chunks.findEmbeddedChunksByKnowledgeBaseId("failed")).thenThrow(failure);
        assertThatThrownBy(() -> adapter.observations("failed")).isSameAs(failure);
    }

    @Test
    void compatibilityAggregatesOnlyIncompatibleIdsAndDoesNotSwallowProviderFailures() {
        EmbeddingTarget target = EmbeddingTarget.derive("https://model.example/v1", "alias", 768, null);
        EmbeddingCompatibility compatibility = new EmbeddingCompatibility(id -> switch (id) {
            case "compatible" -> List.of(new StoredEmbeddingObservation(target.id(), "alias", null));
            case "legacy" -> List.of(new StoredEmbeddingObservation(null, "alias", null));
            case "failure" -> throw new IllegalStateException("read failed");
            default -> List.of();
        });
        assertThat(compatibility.hasEmbeddedChunks("empty")).isFalse();
        assertThat(compatibility.hasEmbeddedChunks("legacy")).isTrue();
        compatibility.requireCompatible("empty", target);
        compatibility.requireCompatible("compatible", target);
        assertThat(compatibility.incompatibleKnowledgeBaseIds(List.of("compatible", "legacy", "empty"), target)).containsExactly("legacy");
        assertThatThrownBy(() -> compatibility.requireCompatible("legacy", target)).isInstanceOf(EmbeddingSpaceConflictException.class);
        assertThatThrownBy(() -> compatibility.incompatibleKnowledgeBaseIds(List.of("legacy", "failure"), target)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignmentsKeepPresenceOrderAndOwnedScopeAndPropagateFailures() {
        KnowledgeBaseRepository knowledgeBases = mock(KnowledgeBaseRepository.class);
        ProfileAssignmentsAdapter adapter = new ProfileAssignmentsAdapter(new AiProfileAssignmentsFacade(knowledgeBases));
        when(knowledgeBases.existsAiProfileAssignment("profile")).thenReturn(true);
        when(knowledgeBases.findIdsByActiveAiProfileId("profile")).thenReturn(List.of("kb-b", "kb-a"));
        assertThat(adapter.exists("profile")).isTrue();
        assertThat(adapter.exists("empty")).isFalse();
        assertThat(adapter.knowledgeBaseIds("profile")).containsExactly("kb-b", "kb-a");
        when(knowledgeBases.findIdsByActiveAiProfileId("empty")).thenReturn(null);
        assertThat(adapter.knowledgeBaseIds("empty")).isEmpty();
        RuntimeException failure = new IllegalStateException("lookup failed");
        when(knowledgeBases.findIdsByActiveAiProfileId("failed")).thenThrow(failure);
        when(knowledgeBases.existsAiProfileAssignment("failed")).thenThrow(failure);
        assertThatThrownBy(() -> adapter.knowledgeBaseIds("failed")).isSameAs(failure);
        assertThatThrownBy(() -> adapter.exists("failed")).isSameAs(failure);
    }

    @Test
    void requiredCapabilitiesCannotBeNullOrBypassedThroughConvenienceConstructors() {
        assertThatThrownBy(() -> new EmbeddingCompatibility(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy(null)).isInstanceOf(NullPointerException.class);
        assertThat(io.github.vfedoriv.graphrag.service.KnowledgeBaseService.class.getConstructors()).hasSize(1);
        assertThat(io.github.vfedoriv.graphrag.service.AiProfileService.class.getConstructors()).hasSize(1);
        assertThatThrownBy(() -> new io.github.vfedoriv.graphrag.service.AiProfileService(
            mock(AiProfileRepository.class), null, null, new EmbeddingCompatibility(id -> List.of()), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void documentLifecycleMapsOwnedCountAndCleanupScopeAndPropagatesFailures() {
        DocumentUploadRepository records = mock(DocumentUploadRepository.class);
        GraphArtifactCleanupService cleanup = mock(GraphArtifactCleanupService.class);
        KnowledgeBaseDocumentsAdapter adapter = new KnowledgeBaseDocumentsAdapter(new KnowledgeBaseDocumentsFacade(records, cleanup));
        when(records.countByKnowledgeBaseId("owned")).thenReturn(3L);
        assertThat(adapter.countByKnowledgeBaseId("owned")).isEqualTo(3);
        adapter.cleanupKnowledgeBaseArtifacts("owned");
        verify(cleanup).cleanupKnowledgeBaseArtifacts("owned");
        RuntimeException failure = new IllegalStateException("cleanup failed");
        when(cleanup.cleanupKnowledgeBaseArtifacts("failed")).thenThrow(failure);
        when(records.countByKnowledgeBaseId("failed")).thenThrow(failure);
        assertThatThrownBy(() -> adapter.cleanupKnowledgeBaseArtifacts("failed")).isSameAs(failure);
        assertThatThrownBy(() -> adapter.countByKnowledgeBaseId("failed")).isSameAs(failure);
    }
}
