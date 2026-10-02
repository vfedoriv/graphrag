package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.SchemaActivationReprocessing;

import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.domain.SchemaFormat;
import io.github.vfedoriv.graphrag.domain.SchemaSourceType;
import io.github.vfedoriv.graphrag.error.ConflictException;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import io.github.vfedoriv.graphrag.schemas.registry.ports.KnowledgeBaseAdmission;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociation;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaAssociations;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaKnowledgeBase;
import io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaParser;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaValidationException;
import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.function.Consumer;

class SchemaRegistryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void schedulesReprocessingOnlyAfterCommittedActivation() {
        SchemaDefinitionRepository definitions = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);
        SchemaActivationReprocessing plans = Mockito.mock(io.github.vfedoriv.graphrag.schemas.reprocessing.contracts.SchemaActivationReprocessing.class);
        ObjectProvider<SchemaActivationReprocessing> provider = Mockito.mock(ObjectProvider.class);
        when(definitions.findById("schema-01"))
            .thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(admission.provision("kb-01", "kb-kb-01"))
            .thenReturn(new SchemaKnowledgeBase("kb-01", null),
                new SchemaKnowledgeBase("kb-01", "schema-01"));
        Mockito.doAnswer(invocation -> {
            Consumer<SchemaActivationReprocessing> consumer = invocation.getArgument(0);
            consumer.accept(plans);
            return null;
        }).when(provider).ifAvailable(any());
        SchemaRegistryService service = new SchemaRegistryService(new SchemaParser(),
            Mockito.mock(SchemaValidator.class), definitions, admission, associations, provider);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.activateSchema("kb-01", "schema-01");
            verify(plans, never()).createForActivation(any(), any());
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            callbacks.getFirst().afterCommit();
            verify(plans).createForActivation("kb-01", "schema-01");
            service.activateSchema("kb-01", "schema-01");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(associations).activate("kb-01", "schema-01");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rolledBackActivationDoesNotScheduleReprocessing() {
        SchemaDefinitionRepository definitions = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);
        ObjectProvider<SchemaActivationReprocessing> provider = Mockito.mock(ObjectProvider.class);
        when(definitions.findById("schema-01"))
            .thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(admission.provision("kb-01", "kb-kb-01"))
            .thenReturn(new SchemaKnowledgeBase("kb-01", null));
        SchemaRegistryService service = new SchemaRegistryService(new SchemaParser(),
            Mockito.mock(SchemaValidator.class), definitions, admission, associations, provider);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.activateSchema("kb-01", "schema-01");
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        Mockito.verifyNoInteractions(provider);
    }

    @Test
    void createSchemaPersistsNewSchemaWhenIdentityDoesNotExist() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        String json = schemaJson("contracts", 1, "Contract");
        when(schemaParser.parse(json)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.existsByNameAndVersion("contracts", 1)).thenReturn(false);
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        SchemaDefinitionNode created = service.createSchema(json, SchemaSourceType.PREDEFINED);

        assertThat(created.getName()).isEqualTo("contracts");
        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getContent()).isEqualTo(json);
        assertThat(created.getContentHash()).hasSize(64);
        verify(schemaRepository).save(any(SchemaDefinitionNode.class));
    }

    @Test
    void createSchemaRejectsExistingIdentityWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        String json = schemaJson("contracts", 1, "Contract");
        when(schemaParser.parse(json)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.existsByNameAndVersion("contracts", 1)).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.createSchema(json, SchemaSourceType.PREDEFINED))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Schema version is immutable and already exists for name=contracts, version=1");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void createSchemaWithKnowledgeBaseAssociatesSavedSchema() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        String json = schemaJson("contracts", 1, "Contract");
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setId("kb-01");
        when(admission.requireManaged("kb-01")).thenReturn(new SchemaKnowledgeBase("kb-01", null));
        when(schemaParser.parse(json)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.existsByNameAndVersion("contracts", 1)).thenReturn(false);
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        SchemaDefinitionNode created = service.createSchema(json, SchemaSourceType.GENERATED, "kb-01");

        assertThat(created.getName()).isEqualTo("contracts");
        verify(admission).requireManaged("kb-01");
        verify(associations).attach("kb-01", created.getId());
    }

    @Test
    void createSchemaWithMissingKnowledgeBaseThrowsWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(admission.requireManaged("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.createSchema(schemaJson("contracts", 1, "Contract"), SchemaSourceType.GENERATED, "missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
        verify(schemaParser, never()).parse(any());
        verify(schemaRepository, never()).save(any());
        verify(associations, never()).attach(any(), any());
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsAssociatedSchemas() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        SchemaDefinitionNode schema = new SchemaDefinitionNode();
        schema.setId("schema-01");

        when(admission.requireManaged("kb-01")).thenReturn(new SchemaKnowledgeBase("kb-01", null));
        when(associations.associations("kb-01")).thenReturn(List.of(new SchemaAssociation("schema-01", false)));
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schema));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        List<SchemaDefinitionNode> result = service.listSchemasByKnowledgeBase("kb-01");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo("schema-01");
        verify(admission).requireManaged("kb-01");
        verify(associations).associations("kb-01");
    }

    @Test
    void listSchemasByKnowledgeBaseReturnsEmptyWhenNoAssociations() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(admission.requireManaged("kb-empty")).thenReturn(new SchemaKnowledgeBase("kb-empty", null));
        when(associations.associations("kb-empty")).thenReturn(List.of());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        List<SchemaDefinitionNode> result = service.listSchemasByKnowledgeBase("kb-empty");

        assertThat(result).isEmpty();
        verify(admission).requireManaged("kb-empty");
        verify(associations).associations("kb-empty");
    }

    @Test
    void listSchemasByKnowledgeBaseThrowsWhenKnowledgeBaseMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(admission.requireManaged("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.listSchemasByKnowledgeBase("missing-kb"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
    }

    @Test
    void attachSchemaAssociatesExistingSchemaWithoutSavingSchemaOrKnowledgeBase() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        SchemaDefinitionNode schema = schemaNode("schema-01", "contracts", 1);
        KnowledgeBaseNode knowledgeBase = new KnowledgeBaseNode();
        knowledgeBase.setId("kb-01");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schema));
        when(admission.requireManaged("kb-01")).thenReturn(new SchemaKnowledgeBase("kb-01", null));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        service.attachSchema("kb-01", "schema-01");

        verify(associations).attach("kb-01", "schema-01");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void attachSchemaThrowsWhenSchemaMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("missing-schema")).thenReturn(Optional.empty());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.attachSchema("kb-01", "missing-schema"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing-schema");
        verify(admission, never()).requireManaged(any());
        verify(associations, never()).attach(any(), any());
    }

    @Test
    void attachSchemaThrowsWhenKnowledgeBaseMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(admission.requireManaged("missing-kb"))
            .thenThrow(new NotFoundException("Knowledge base not found: missing-kb"));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.attachSchema("missing-kb", "schema-01"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Knowledge base not found: missing-kb");
        verify(associations, never()).attach(any(), any());
    }

    @Test
    void updateSchemaPersistsReplacementContentAndHashForInactiveSchema() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        SchemaDefinitionNode existing = schemaNode("schema-01", "contracts", 1);
        String updatedJson = schemaJson("contracts", 1, "Agreement");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(existing));
        when(associations.hasActiveReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(updatedJson)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());
        when(schemaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        SchemaDefinitionNode updated = service.updateSchema("schema-01", updatedJson, SchemaSourceType.GENERATED);

        assertThat(updated.getId()).isEqualTo("schema-01");
        assertThat(updated.getContent()).isEqualTo(updatedJson);
        assertThat(updated.getContentHash()).hasSize(64);
        assertThat(updated.getSourceType()).isEqualTo(SchemaSourceType.GENERATED);
        assertThat(updated.getFormat()).isEqualTo(SchemaFormat.JSON);
        verify(schemaRepository).save(existing);
    }

    @Test
    void updateSchemaRejectsInvalidContentWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        String invalidJson = schemaJson("contracts", 1, "Contract");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(associations.hasActiveReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(invalidJson)).thenReturn(schemaDocument("contracts", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of("Node key must be declared as a property"));

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", invalidJson, SchemaSourceType.GENERATED))
            .isInstanceOf(SchemaValidationException.class);
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void updateSchemaThrowsWhenMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("missing")).thenReturn(Optional.empty());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.updateSchema("missing", schemaJson("contracts", 1, "Contract"), SchemaSourceType.GENERATED))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing");
    }

    @Test
    void updateSchemaRejectsIdentityChangeWithoutSaving() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        String updatedJson = schemaJson("contracts-renamed", 1, "Contract");
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(associations.hasActiveReference("schema-01")).thenReturn(false);
        when(schemaParser.parse(updatedJson)).thenReturn(schemaDocument("contracts-renamed", 1));
        when(schemaValidator.validate(any())).thenReturn(List.of());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", updatedJson, SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Schema identity is immutable");
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void updateSchemaRejectsActiveSchemaBeforeParsing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(associations.hasActiveReference("schema-01")).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.updateSchema("schema-01", schemaJson("contracts", 1, "Contract"), SchemaSourceType.GENERATED))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot update active schema: schema-01");
        verify(schemaParser, never()).parse(any());
        verify(schemaRepository, never()).save(any());
    }

    @Test
    void deleteSchemaDetachesRelationshipsAndDeletesInactiveSchema() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        SchemaDefinitionNode schema = schemaNode("schema-01", "contracts", 1);
        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schema));
        when(associations.hasActiveReference("schema-01")).thenReturn(false);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        service.deleteSchema("schema-01");

        verify(associations).detach("schema-01");
        verify(schemaRepository).delete(schema);
    }

    @Test
    void deleteSchemaThrowsWhenMissing() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("missing")).thenReturn(Optional.empty());

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.deleteSchema("missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Schema not found: missing");
    }

    @Test
    void deleteSchemaRejectsActiveSchemaWithoutDetaching() {
        SchemaParser schemaParser = Mockito.mock(SchemaParser.class);
        SchemaValidator schemaValidator = Mockito.mock(SchemaValidator.class);
        SchemaDefinitionRepository schemaRepository = Mockito.mock(SchemaDefinitionRepository.class);
        KnowledgeBaseAdmission admission = Mockito.mock(KnowledgeBaseAdmission.class);
        SchemaAssociations associations = Mockito.mock(SchemaAssociations.class);

        when(schemaRepository.findById("schema-01")).thenReturn(Optional.of(schemaNode("schema-01", "contracts", 1)));
        when(associations.hasActiveReference("schema-01")).thenReturn(true);

        SchemaRegistryService service = new SchemaRegistryService(
            schemaParser,
            schemaValidator,
            schemaRepository,
            admission,
            associations,
            Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
        );

        assertThatThrownBy(() -> service.deleteSchema("schema-01"))
            .isInstanceOf(ConflictException.class)
            .hasMessage("Cannot delete active schema: schema-01");
        verify(associations, never()).detach(any());
        verify(schemaRepository, never()).delete(any());
    }

    private SchemaDefinitionNode schemaNode(String id, String name, int version) {
        SchemaDefinitionNode node = new SchemaDefinitionNode();
        node.setId(id);
        node.setName(name);
        node.setVersion(version);
        node.setSourceType(SchemaSourceType.PREDEFINED);
        node.setFormat(SchemaFormat.JSON);
        node.setContent(schemaJson(name, version, "Contract"));
        node.setContentHash("old-hash");
        return node;
    }

    private SchemaDocument schemaDocument(String name, int version) {
        return new SchemaDocument(name, version, null, List.of(), List.of(), List.of(), List.of());
    }

    private String schemaJson(String name, int version, String label) {
        return """
            {
              "name": "%s",
              "version": %d,
              "nodes": [
                {"label": "%s", "key": "id", "properties": [{"name": "id", "type": "string"}]}
              ],
              "relationships": []
            }
            """.formatted(name, version, label);
    }
}
