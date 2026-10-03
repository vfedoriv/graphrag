package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseLifecycleService;

import io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeBaseLifecycleServiceTest {
    @Test
    void provisionsDefaultProfile() {
        KnowledgeBaseRepository repository = mock(KnowledgeBaseRepository.class);
        AiProfileService aiProfileService = mock(AiProfileService.class);
        AiProfileNode profile = new AiProfileNode();
        profile.setId("default");
        when(aiProfileService.defaultFacts()).thenReturn(profile.facts());
        when(repository.findById("kb-1")).thenReturn(Optional.empty());
        when(repository.save(any(KnowledgeBaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        KnowledgeBaseLifecycleService service = new KnowledgeBaseLifecycleService(repository, aiProfileService);

        KnowledgeBaseNode provisioned = service.provision("kb-1", "KB 1");

        assertThat(provisioned.getActiveAiProfileId()).isEqualTo("default");
        assertThat(provisioned.getCreatedAt()).isNotNull();
        verify(repository).save(any(KnowledgeBaseNode.class));
    }
}
