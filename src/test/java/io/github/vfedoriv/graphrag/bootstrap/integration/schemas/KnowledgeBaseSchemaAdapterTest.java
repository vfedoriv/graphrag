package io.github.vfedoriv.graphrag.bootstrap.integration.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaAccess;
import io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseSchemaFacts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class KnowledgeBaseSchemaAdapterTest {
    @Test
    void mapsImmutableAssociationFacts() {
        KnowledgeBaseSchemaAccess access = mock(KnowledgeBaseSchemaAccess.class);
        when(access.associations("kb-1")).thenReturn(new KnowledgeBaseSchemaFacts.Associations(
            List.of(new KnowledgeBaseSchemaFacts.Association("schema-1", true))));

        assertThat(new KnowledgeBaseSchemaAdapter(access).associations("kb-1"))
            .containsExactly(new io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociation("schema-1", true));
    }

    @Test
    void preservesAdmissionErrorAndAddsNoTransactionBoundary() {
        KnowledgeBaseSchemaAccess access = mock(KnowledgeBaseSchemaAccess.class);
        when(access.requireManaged("missing"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing"));

        assertThatThrownBy(() -> new KnowledgeBaseSchemaAdapter(access).requireManaged("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing");
        assertThat(KnowledgeBaseSchemaAdapter.class.isAnnotationPresent(Transactional.class)).isFalse();
    }
}
