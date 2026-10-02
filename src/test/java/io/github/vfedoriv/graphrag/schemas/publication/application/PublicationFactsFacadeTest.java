package io.github.vfedoriv.graphrag.schemas.publication.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.vfedoriv.graphrag.schemas.publication.contracts.PublicationFacts;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.schemas.publication.domain.SchemaDraftPublicationStatus;
import io.github.vfedoriv.graphrag.schemas.publication.ports.SchemaDraftPublicationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PublicationFactsFacadeTest {
    private final SchemaDraftPublicationRepository repository = mock(SchemaDraftPublicationRepository.class);
    private final PublicationFactsFacade facts = new PublicationFactsFacade(repository);

    @Test void capturesDetachedIdentityFactsFromEitherLookupWithoutLeakingMutableRecords() {
        SchemaDraftPublicationNode publication = new SchemaDraftPublicationNode();
        publication.setId("publication"); publication.setDraftId("draft"); publication.setKnowledgeBaseId("kb");
        publication.setSchemaId("schema"); publication.setDraftRevision(7); publication.setAggregateRevisionId("aggregate");
        publication.setProjectionContentHash("hash"); publication.setTargetIdentity("target:1");
        publication.setStatus(SchemaDraftPublicationStatus.COMPLETED); publication.setCreatedAt(Instant.EPOCH);
        when(repository.findByDraftId("draft")).thenReturn(Optional.of(publication));
        when(repository.findBySchemaId("schema")).thenReturn(Optional.of(publication));
        PublicationFacts.Publication snapshot = facts.findByDraftId("draft").orElseThrow();
        assertThat(facts.findBySchemaId("schema")).contains(snapshot);
        publication.setSchemaId("changed"); publication.setProjectionContentHash("changed");
        assertThat(snapshot).isEqualTo(new PublicationFacts.Publication("publication", "draft", "kb", "schema", 7,
            "aggregate", "hash", "target:1", true, Instant.EPOCH));
    }

    @Test void preservesMissingFacts() {
        assertThat(facts.findByDraftId("missing")).isEmpty();
        assertThat(facts.findBySchemaId("missing")).isEmpty();
    }
}
