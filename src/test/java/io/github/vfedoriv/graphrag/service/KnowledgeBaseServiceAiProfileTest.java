package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.document.chunking.TokenizerId;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.KnowledgeBaseNotEmptyException;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceAiProfileTest {

    @Test
    void newKnowledgeBaseReceivesDefaultAiProfile() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        AiProfileNode defaultProfile = profile("default", "embed-default", 1536);
        when(aiProfileService.defaultProfile()).thenReturn(defaultProfile);
        when(knowledgeBaseRepository.save(any(KnowledgeBaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            aiProfileService,
            chunkRepository
        );

        KnowledgeBaseNode created = service.create("kb-1", "KB 1");

        assertThat(created.getActiveAiProfileId()).isEqualTo("default");
    }

    @Test
    void compatibleProfileAssignmentIsPersisted() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeBaseNode knowledgeBase = knowledgeBase("kb-1", "profile-old");
        AiProfileNode compatible = profile("profile-new", "embed-default", 1536);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(aiProfileService.getNode("profile-new")).thenReturn(compatible);
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-1")).thenReturn(List.of(chunk("embed-default", 1536)));
        when(knowledgeBaseRepository.save(any(KnowledgeBaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            aiProfileService,
            chunkRepository
        );

        KnowledgeBaseNode updated = service.updateActiveAiProfile("kb-1", "profile-new");

        assertThat(updated.getActiveAiProfileId()).isEqualTo("profile-new");
        verify(knowledgeBaseRepository).save(knowledgeBase);
    }

    @Test
    void incompatibleProfileAssignmentIsRejectedAndPreviousProfileRemains() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeBaseNode knowledgeBase = knowledgeBase("kb-1", "profile-old");
        AiProfileNode incompatible = profile("profile-new", "other-embed", 768);
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(aiProfileService.getNode("profile-new")).thenReturn(incompatible);
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-1")).thenReturn(List.of(chunk("embed-default", 1536)));
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            aiProfileService,
            chunkRepository
        );

        assertThatThrownBy(() -> service.updateActiveAiProfile("kb-1", "profile-new"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("incompatible");

        assertThat(knowledgeBase.getActiveAiProfileId()).isEqualTo("profile-old");
        verify(knowledgeBaseRepository, never()).save(any(KnowledgeBaseNode.class));
    }

    @Test
    void rejectsEqualDimensionProfileAssignmentFromAnotherProvider() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeBaseNode knowledgeBase = knowledgeBase("kb-1", "profile-old");
        AiProfileNode incompatible = profile("profile-new", "embed-default", 1536);
        incompatible.setBaseUrl("https://other-provider.example/v1");
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(aiProfileService.getNode("profile-new")).thenReturn(incompatible);
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-1"))
            .thenReturn(List.of(chunk("embed-default", 1536)));
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            aiProfileService,
            chunkRepository
        );

        assertThatThrownBy(() -> service.updateActiveAiProfile("kb-1", "profile-new"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("embedding space");

        verify(knowledgeBaseRepository, never()).save(any(KnowledgeBaseNode.class));
    }

    @Test
    void rejectsProfileAssignmentWhenResolvedTokenizerChanges() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        KnowledgeBaseNode knowledgeBase = knowledgeBase("kb-1", "profile-old");
        AiProfileNode incompatible = profile("profile-new", "embedding-alias", 768);
        incompatible.setTokenizerId(new TokenizerId(TokenizerId.CL100K_BASE));
        when(knowledgeBaseRepository.findById("kb-1")).thenReturn(Optional.of(knowledgeBase));
        when(aiProfileService.getNode("profile-new")).thenReturn(incompatible);
        when(chunkRepository.findEmbeddedChunksByKnowledgeBaseId("kb-1"))
            .thenReturn(List.of(chunk("embedding-alias", 768)));
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            aiProfileService,
            chunkRepository
        );

        assertThatThrownBy(() -> service.updateActiveAiProfile("kb-1", "profile-new"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("incompatible");

        assertThat(knowledgeBase.getActiveAiProfileId()).isEqualTo("profile-old");
        verify(knowledgeBaseRepository, never()).save(any(KnowledgeBaseNode.class));
    }

    @Test
    void rejectsDeletionWhenDocumentsRemain() {
        KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
        DocumentUploadRepository documentUploadRepository = mock(DocumentUploadRepository.class);
        when(knowledgeBaseRepository.existsById("kb-1")).thenReturn(true);
        when(documentUploadRepository.countByKnowledgeBaseId("kb-1")).thenReturn(2L);
        KnowledgeBaseService service = new KnowledgeBaseService(
            knowledgeBaseRepository,
            mock(AiProfileService.class),
            mock(DocumentChunkRepository.class),
            documentUploadRepository,
            mock(KnowledgeBaseLifecycleService.class),
            new EmbeddingSpacePolicy(mock(DocumentChunkRepository.class))
        );

        assertThatThrownBy(() -> service.delete("kb-1"))
            .isInstanceOf(KnowledgeBaseNotEmptyException.class)
            .hasMessageContaining("2 document");
    }

    private KnowledgeBaseNode knowledgeBase(String id, String profileId) {
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setId(id);
        knowledgeBase.setName(id);
        knowledgeBase.setActiveAiProfileId(profileId);
        return knowledgeBase;
    }

    private AiProfileNode profile(String id, String embeddingModel, int dimensions) {
        AiProfileNode profile = new AiProfileNode();
        profile.setId(id);
        profile.setBaseUrl("https://api.openai.com/v1");
        profile.setEmbeddingModel(embeddingModel);
        profile.setEmbeddingDimensions(dimensions);
        return profile;
    }

    private DocumentChunkNode chunk(String embeddingModel, int dimensions) {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setEmbeddingModel(embeddingModel);
        chunk.setEmbeddingDimensions(dimensions);
        chunk.setEmbeddingSpaceId(EmbeddingSpaceIdentity.derive("https://api.openai.com/v1", embeddingModel, dimensions).id());
        return chunk;
    }
}
