package io.github.vfedoriv.graphrag.schemas.drafts.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.schemas.drafts.contracts.DraftPublicationLink;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.*;
import io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;

class DraftPublicationLinkFacadeTest {
    private final SchemaDraftLifecycleService lifecycle = mock(SchemaDraftLifecycleService.class);
    private final SchemaDraftRepository repository = mock(SchemaDraftRepository.class);
    private final DraftPublicationLinkFacade facade = new DraftPublicationLinkFacade(lifecycle, repository);

    @Test void completesLinkWithoutAdvancingAuthoringRevisionAndPreservesOptimisticVersion() {
        SchemaDraftNode draft = draft();
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft);
        Instant completedAt = Instant.parse("2026-01-02T03:04:05Z");
        facade.complete(new DraftPublicationLink.Completion("kb", "draft", 7, "aggregate", 3L, "schema", "hash", completedAt));
        ArgumentCaptor<SchemaDraftNode> saved = ArgumentCaptor.forClass(SchemaDraftNode.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SchemaDraftStatus.PUBLISHED);
        assertThat(saved.getValue().getRevision()).isEqualTo(7);
        assertThat(saved.getValue().getCurrentAggregateId()).isEqualTo("aggregate");
        assertThat(saved.getValue().getPublicationSchemaId()).isEqualTo("schema");
        assertThat(saved.getValue().getPublicationContentHash()).isEqualTo("hash");
        assertThat(saved.getValue().getPersistenceVersion()).isEqualTo(3L);
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(completedAt);
    }

    @Test void rejectsAConcurrentAuthoringSaveBeforeChangingTheDraft() {
        SchemaDraftNode draft = draft();
        draft.setPersistenceVersion(4L);
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft);
        assertThatThrownBy(() -> facade.complete(new DraftPublicationLink.Completion("kb", "draft", 7,
            "aggregate", 3L, "schema", "hash", Instant.now()))).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(draft.getStatus()).isEqualTo(SchemaDraftStatus.OPEN);
        verifyNoInteractions(repository);
    }

    @Test void rejectsMismatchedRevisionOrAggregatePreconditions() {
        SchemaDraftNode draft = draft();
        when(lifecycle.requireOwned("kb", "draft")).thenReturn(draft);
        assertThatThrownBy(() -> facade.complete(new DraftPublicationLink.Completion("kb", "draft", 6,
            "aggregate", 3L, "schema", "hash", Instant.now()))).isInstanceOf(OptimisticLockingFailureException.class);
        assertThatThrownBy(() -> facade.complete(new DraftPublicationLink.Completion("kb", "draft", 7,
            "another-aggregate", 3L, "schema", "hash", Instant.now()))).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(draft.getStatus()).isEqualTo(SchemaDraftStatus.OPEN);
        verifyNoInteractions(repository);
    }

    private SchemaDraftNode draft() {
        SchemaDraftNode draft = new SchemaDraftNode();
        draft.setId("draft"); draft.setKnowledgeBaseId("kb"); draft.setRevision(7);
        draft.setPersistenceVersion(3L); draft.setCurrentAggregateId("aggregate");
        draft.setStatus(SchemaDraftStatus.OPEN);
        return draft;
    }
}
