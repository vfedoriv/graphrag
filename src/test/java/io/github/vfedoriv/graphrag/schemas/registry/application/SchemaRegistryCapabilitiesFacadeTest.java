package io.github.vfedoriv.graphrag.schemas.registry.application;

import io.github.vfedoriv.graphrag.http.contracts.ConflictException;

import io.github.vfedoriv.graphrag.schemas.contracts.SchemaRegistryCapabilities;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.contracts.StoredSchemaSnapshots;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaValidationResult;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaFormat;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaStatus;
import io.github.vfedoriv.graphrag.schemas.registry.ports.KnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociations;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.SchemaActivationReprocessing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaRegistryCapabilitiesFacadeTest {
    private static final String VALID_SCHEMA_JSON = """
        {"name":"contracts","version":1,"nodes":[{"label":"Contract","key":"id","properties":[{"name":"id","type":"string","required":true}]}],"relationships":[]}
        """;

    @Test
    void parseAndValidateReturnsAnImmutableParsedDefinitionAndValidationErrors() {
        SchemaRegistryService registry = mock(SchemaRegistryService.class);
        StoredSchemaSnapshots stored = mock(StoredSchemaSnapshots.class);
        SchemaAssociations associations = mock(SchemaAssociations.class);
        SchemaRegistryCapabilities capabilities = new SchemaRegistryCapabilitiesFacade(
            registry, new SchemaParser(), new SchemaValidator(), stored, associations);

        SchemaValidationResult result = capabilities.parseAndValidate(VALID_SCHEMA_JSON);

        assertThat(result.schema().name()).isEqualTo("contracts");
        assertThat(result.schema().nodes()).hasSize(1);
        assertThat(result.errors()).isEmpty();
        assertThatThrownBy(() -> result.schema().nodes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.schema().nodes().get(0).properties().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors().add("changed"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void globalIdentityOccupancyIsIndependentOfKnowledgeBaseAssociation() {
        SchemaDefinitionRepository definitions = mock(SchemaDefinitionRepository.class);
        SchemaRegistryService registry = registry(definitions, mock(KnowledgeBaseAdmission.class), mock(SchemaAssociations.class));
        StoredSchemaSnapshots stored = mock(StoredSchemaSnapshots.class);
        SchemaRegistryCapabilities capabilities = capabilities(registry, stored, mock(SchemaAssociations.class));
        when(definitions.existsByNameAndVersion("contracts", 1)).thenReturn(true);
        when(stored.associated("kb-1")).thenReturn(List.of());

        assertThat(capabilities.identityExistsGlobally("contracts", 1)).isTrue();
        assertThat(capabilities.findAssociatedByIdentity("kb-1", "contracts", 1)).isEmpty();
    }

    @Test
    void associatedIdentityLookupReturnsOnlyTheRequestedKnowledgeBaseIdentity() {
        SchemaRegistryService registry = mock(SchemaRegistryService.class);
        StoredSchemaSnapshots stored = mock(StoredSchemaSnapshots.class);
        SchemaRegistryCapabilities capabilities = capabilities(registry, stored, mock(SchemaAssociations.class));
        SchemaSnapshot requested = snapshot("kb-1", "schema-1", "contracts", 2, SchemaStatus.INACTIVE);
        when(stored.associated("kb-1")).thenReturn(List.of(
            snapshot("kb-1", "schema-other", "other", 2, SchemaStatus.ACTIVE),
            requested,
            snapshot("kb-2", "schema-wrong-scope", "contracts", 2, SchemaStatus.ACTIVE)));

        assertThat(capabilities.findAssociatedByIdentity("kb-1", "contracts", 2)).contains(requested);
    }

    @Test
    void storedLookupReturnsEmptyForMissingSchemasAndUsesGlobalActiveStatus() {
        SchemaRegistryService registry = mock(SchemaRegistryService.class);
        StoredSchemaSnapshots stored = mock(StoredSchemaSnapshots.class);
        SchemaAssociations associations = mock(SchemaAssociations.class);
        SchemaRegistryCapabilities capabilities = capabilities(registry, stored, associations);
        when(stored.findById("missing")).thenReturn(Optional.empty());
        when(stored.findById("schema-1"))
            .thenReturn(Optional.of(snapshot(null, "schema-1", "contracts", 1, SchemaStatus.INACTIVE)));
        when(associations.hasActiveReference("schema-1")).thenReturn(true);

        assertThat(capabilities.findStoredById("missing")).isEmpty();
        assertThat(capabilities.findStoredById("schema-1")).get()
            .extracting(SchemaSnapshot::status)
            .isEqualTo(SchemaStatus.ACTIVE);
    }

    @Test
    void generatedRegistrationReturnsAnAssociatedInactiveImmutableSnapshot() {
        SchemaDefinitionRepository definitions = mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = mock(SchemaAssociations.class);
        SchemaRegistryService registry = registry(definitions, admission, associations);
        SchemaRegistryCapabilities capabilities = capabilities(
            registry, mock(StoredSchemaSnapshots.class), associations);
        when(admission.requireManaged("kb-1")).thenReturn(new SchemaKnowledgeBase("kb-1", null));
        when(definitions.existsByNameAndVersion("contracts", 1)).thenReturn(false);
        when(definitions.save(any(SchemaDefinitionNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaSnapshot registered = capabilities.registerGeneratedInactive(VALID_SCHEMA_JSON, "kb-1");

        assertThat(registered.knowledgeBaseId()).isEqualTo("kb-1");
        assertThat(registered.name()).isEqualTo("contracts");
        assertThat(registered.version()).isEqualTo(1);
        assertThat(registered.sourceType()).isEqualTo(SchemaSourceType.GENERATED);
        assertThat(registered.status()).isEqualTo(SchemaStatus.INACTIVE);
        assertThat(registered.content()).isEqualTo(VALID_SCHEMA_JSON);
        assertThat(registered.contentHash()).hasSize(64);
        assertThat(registered.schema()).isNull();
        verify(associations).attach("kb-1", registered.schemaDefinitionId());
    }

    @Test
    void generatedRegistrationPreservesGlobalIdentityConflictBehavior() {
        SchemaDefinitionRepository definitions = mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = mock(SchemaAssociations.class);
        SchemaRegistryService registry = registry(definitions, admission, associations);
        SchemaRegistryCapabilities capabilities = capabilities(
            registry, mock(StoredSchemaSnapshots.class), associations);
        when(admission.requireManaged("kb-1")).thenReturn(new SchemaKnowledgeBase("kb-1", null));
        when(definitions.existsByNameAndVersion("contracts", 1)).thenReturn(true);

        assertThatThrownBy(() -> capabilities.registerGeneratedInactive(VALID_SCHEMA_JSON, "kb-1"))
            .isInstanceOf(io.github.vfedoriv.graphrag.http.contracts.ConflictException.class)
            .hasMessage("Schema version is immutable and already exists for name=contracts, version=1");
        verify(definitions, never()).save(any(SchemaDefinitionNode.class));
        verify(associations, never()).attach(any(), any());
    }

    private SchemaRegistryCapabilities capabilities(
        SchemaRegistryService registry,
        StoredSchemaSnapshots stored,
        SchemaAssociations associations
    ) {
        return new SchemaRegistryCapabilitiesFacade(
            registry, new SchemaParser(), new SchemaValidator(), stored, associations);
    }

    private SchemaRegistryService registry(
        SchemaDefinitionRepository definitions,
        KnowledgeBaseAdmission admission,
        SchemaAssociations associations
    ) {
        ObjectProvider<SchemaActivationReprocessing> provider = mock(ObjectProvider.class);
        return new SchemaRegistryService(
            new SchemaParser(), new SchemaValidator(), definitions, admission, associations, provider);
    }

    private SchemaSnapshot snapshot(
        String knowledgeBaseId,
        String schemaId,
        String name,
        int version,
        SchemaStatus status
    ) {
        return new SchemaSnapshot(
            knowledgeBaseId, schemaId, name, version, SchemaSourceType.GENERATED, SchemaFormat.JSON,
            status, "{stored}", "hash", Instant.parse("2026-01-01T00:00:00Z"), null, null);
    }
}
