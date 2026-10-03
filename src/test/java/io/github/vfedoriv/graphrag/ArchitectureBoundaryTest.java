package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.schemas.registry.api.SchemaController;
import io.github.vfedoriv.graphrag.schemas.discovery.api.model.SchemaDiscoveryResponse;
import io.github.vfedoriv.graphrag.bootstrap.SchemaBootstrapService;
import io.github.vfedoriv.graphrag.documents.api.error.ProcessingOptionsValidationException;
import io.github.vfedoriv.graphrag.documents.api.error.GraphExtractionValidationException;
import io.github.vfedoriv.graphrag.http.contracts.PageResponse;
import io.github.vfedoriv.graphrag.bootstrap.PersistenceConfiguration;
import io.github.vfedoriv.graphrag.http.contracts.NotFoundException;
import io.github.vfedoriv.graphrag.http.contracts.ConflictException;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import io.github.vfedoriv.graphrag.settings.api.model.RuntimeSettingResponse;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourcePreparer;

import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import io.github.vfedoriv.graphrag.search.runs.ports.SearchSchemas;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshot;
import io.github.vfedoriv.graphrag.schemas.registry.domain.SchemaDefinitionNode;
import io.github.vfedoriv.graphrag.schemas.contracts.SchemaDocument;
import io.github.vfedoriv.graphrag.schemas.contracts.NodeKeySupport;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional;
import io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {

    private static final String BASE_PACKAGE = "io.github.vfedoriv.graphrag";
    private static final String CONTROLLER_PACKAGE = BASE_PACKAGE + ".controller";
    private static final String DOMAIN_PACKAGE = BASE_PACKAGE + ".domain";
    private static final String DTO_PACKAGE = BASE_PACKAGE + ".dto";
    private static final String REPOSITORY_PACKAGE = BASE_PACKAGE + ".repository";
    private static final String SERVICE_PACKAGE = BASE_PACKAGE + ".service";
    private static final String WORKFLOW_PACKAGE = BASE_PACKAGE + ".workflow";
    private static final String APPLICATION_PACKAGE = BASE_PACKAGE + ".application";
    private static final String INFRASTRUCTURE_PERSISTENCE_PACKAGE = BASE_PACKAGE + ".infrastructure.persistence";
    private static final String RELATIONAL_ENTITY_PACKAGE = INFRASTRUCTURE_PERSISTENCE_PACKAGE + ".relational.entity";
    private static final String RELATIONAL_REPOSITORY_PACKAGE = INFRASTRUCTURE_PERSISTENCE_PACKAGE + ".relational.repository";
    private static final String TRANSACTION_ANNOTATION_PACKAGE = BASE_PACKAGE + ".persistence.transaction";
    private static final Set<String> ALLOWED_SDN_NODE_TYPES = Set.of(
            BASE_PACKAGE + ".documents.adapters.graph.entity.DocumentChunkEntity"
    );
    private static final Set<String> ALLOWED_NEO4J_REPOSITORIES = Set.of(
            BASE_PACKAGE + ".documents.adapters.graph.repository.Neo4jDocumentChunkRepository"
    );


    // Exact direct dependencies retained until roadmap steps 8–9. New callers fail this check.


    // Existing REQUIRED self-call semantics, audited per signature in compatibility-audit.md.
    // These permit no foreign ownership access and cannot authorize a new method or overload.
    private static final Set<String> EXISTING_TRANSACTION_SELF_CALLS = Set.of(
        "io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.delete(java.lang.String) -> io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.getNode(java.lang.String)",
        "io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.get(java.lang.String) -> io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.getNode(java.lang.String)",
        "io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.inspect(java.lang.String) -> io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.getNode(java.lang.String)",
        "io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.require(java.lang.String) -> io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.getNode(java.lang.String)",
        "io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.update(java.lang.String, io.github.vfedoriv.graphrag.ai.profiles.api.model.UpdateAiProfileRequest) -> io.github.vfedoriv.graphrag.ai.profiles.application.AiProfileService.getNode(java.lang.String)",
        "io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.activeAiProfile(java.lang.String) -> io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.get(java.lang.String)",
        "io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.getActiveAiProfile(java.lang.String) -> io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.activeAiProfile(java.lang.String)",
        "io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.update(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.get(java.lang.String)",
        "io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.updateActiveAiProfile(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.knowledgebase.application.KnowledgeBaseService.get(java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.delete(java.lang.String, java.lang.String, long) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireMutable(java.lang.String, java.lang.String, long)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.get(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireOwned(java.lang.String, java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireMutable(java.lang.String, java.lang.String, long) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireOwned(java.lang.String, java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.update(java.lang.String, java.lang.String, io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos$UpdateDraftRequest) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireMutable(java.lang.String, java.lang.String, long)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.updateGuidance(java.lang.String, java.lang.String, io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos$UpdateGuidanceRequest) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftLifecycleService.requireMutable(java.lang.String, java.lang.String, long)",
        "io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftReviewService.diff(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftReviewService.projection(java.lang.String, java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.publication.application.SchemaDraftPublicationService.publishObserved(java.lang.String, java.lang.String, io.github.vfedoriv.graphrag.schemas.publication.api.model.SchemaDraftPublicationDtos$PublishDraftRequest) -> io.github.vfedoriv.graphrag.schemas.publication.application.SchemaDraftPublicationService.readiness(java.lang.String, java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.activateSchema(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.getSchema(java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.attachSchema(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.getSchema(java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.createGeneratedInactiveSchema(java.lang.String, java.lang.String) -> io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.createSchema(java.lang.String, io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType, java.lang.String)",
        "io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.createSchema(java.lang.String, io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType) -> io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService.createSchema(java.lang.String, io.github.vfedoriv.graphrag.schemas.contracts.SchemaSourceType, java.lang.String)",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.chunkingFacts() -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.list()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.clear(java.lang.String) -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.effectiveChunkerRevision()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.documentStorageRootValue() -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.documentStorageRoot()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.effectiveChunkerRevision() -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.chunking()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.list() -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.effectiveChunkerRevision()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.update(java.lang.String, java.lang.Object) -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.effectiveChunkerRevision()",
        "io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.update(java.util.List) -> io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService.effectiveChunkerRevision()"
    );

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE);

    // Exact transitional edges, grouped by their retirement roadmap step.
    // 7 evaluation; 8 search; 9 support/assembly.


    private static String edge(String origin, String target) {
        return BASE_PACKAGE + "." + origin + " -> " + BASE_PACKAGE + "." + target;
    }

    private static Set<String> documentTransitionalEdges(JavaClasses classes) {
        return classes.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> documentTransitionalEdge(d.getOriginClass(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }

    private static boolean documentTransitionalEdge(JavaClass origin, JavaClass target) {
        return !isInPackage(origin, BASE_PACKAGE + ".documents")
                && !isInPackage(origin, BASE_PACKAGE + ".bootstrap")
                && isInPackage(target, BASE_PACKAGE + ".documents")
                && !isInPackage(target, BASE_PACKAGE + ".documents.contracts")
            || FinalSupportBoundaryTest.foreignImplementation(origin, target);
    }


    @Test
    void schema_downstream_workflows_use_owner_contracts() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInAnyPackage(c, BASE_PACKAGE + ".schemas.evaluation", BASE_PACKAGE + ".schemas.publication", BASE_PACKAGE + ".schemas.reprocessing"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> schemaOwnershipDependencyForbidden(d.getTargetClass())
                || isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.drafts.application", BASE_PACKAGE + ".schemas.drafts.domain",
                    BASE_PACKAGE + ".schemas.drafts.adapters", BASE_PACKAGE + ".schemas.drafts.ports", BASE_PACKAGE + ".schemas.registry.application",
                    BASE_PACKAGE + ".schemas.registry.domain", BASE_PACKAGE + ".schemas.registry.adapters", BASE_PACKAGE + ".schemas.registry.ports")
                || Set.of(BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseService", BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseLifecycleService", BASE_PACKAGE + ".ai.profiles.domain.AiProfileNode")
                    .contains(d.getTargetClass().getName()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void evaluation_metrics_are_pure_rules() {
        Set<String> violations = PRODUCTION_CLASSES.get(BASE_PACKAGE + ".schemas.evaluation.application.SchemaDraftEvaluationMetricsCalculator")
            .getDirectDependenciesFromSelf().stream()
            .filter(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE)
                && !isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.evaluation.domain", BASE_PACKAGE + ".schemas.contracts"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void dry_extraction_has_no_processing_or_persistence_writers() {
        Set<String> violations = PRODUCTION_CLASSES.get(BASE_PACKAGE + ".documents.application.processing.DocumentDryExtractionFacade")
            .getDirectDependenciesFromSelf().stream()
            .filter(d -> isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".documents.adapters.graph", BASE_PACKAGE + ".documents.adapters.relational")
                || Set.of("DocumentProcessingService", "GraphExtractionService", "EmbeddingPersistenceStage", "GraphExtractionStage",
                    "ProcessingRunLifecycle", "ExtractionRunLifecycle", "DocumentUploadRepository", "DocumentChunkRepository").contains(d.getTargetClass().getSimpleName()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void schema_public_contracts_exclude_persistence_clients_and_mutable_fields() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".schemas.contracts").stream()
            .filter(d -> schemaContractDependencyForbidden(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(c -> isInPackage(c, BASE_PACKAGE + ".schemas.contracts"))
            .flatMap(c -> c.getFields().stream())
            .filter(field -> !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL))
            .map(field -> field.getFullName()).forEach(violations::add);
        assertNoViolations(violations);
    }

    @Test
    void downstream_public_values_exclude_implementation_dependencies_and_mutable_fields() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(ArchitectureBoundaryTest::isDownstreamPublicValue)
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE)
                && !isDownstreamPublicValue(d.getTargetClass())
                && !isInPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.contracts")
                && !d.getTargetClass().getName().startsWith(BASE_PACKAGE + ".schemas.evaluation.domain.EvaluationObservations$"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream().filter(ArchitectureBoundaryTest::isDownstreamPublicValue)
            .flatMap(c -> c.getFields().stream())
            .filter(field -> !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL))
            .map(field -> field.getFullName()).forEach(violations::add);
        assertNoViolations(violations);
    }

    private static boolean isDownstreamPublicValue(JavaClass type) {
        return type.getName().equals(BASE_PACKAGE + ".schemas.evaluation.domain.EvaluationObservations")
            || type.getName().startsWith(BASE_PACKAGE + ".schemas.evaluation.domain.EvaluationObservations$")
            || type.getName().equals(BASE_PACKAGE + ".knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts")
            || type.getName().startsWith(BASE_PACKAGE + ".knowledgebase.contracts.SchemaWorkflowKnowledgeBaseFacts$")
            || isInAnyPackage(type, BASE_PACKAGE + ".schemas.drafts.contracts", BASE_PACKAGE + ".schemas.evaluation.contracts",
            BASE_PACKAGE + ".schemas.publication.contracts", BASE_PACKAGE + ".schemas.reprocessing.contracts")
            || Set.of("EvaluationDocuments", "EvaluationDryExtraction", "EvaluationProfiles", "ReprocessingKnowledgeBases",
                "DraftEvaluationSummaries", "DraftReprocessingSummaries").stream()
                .map(name -> BASE_PACKAGE + (name.startsWith("Evaluation") ? ".schemas.evaluation.ports."
                    : name.startsWith("Reprocessing") ? ".schemas.reprocessing.ports." : ".schemas.drafts.ports.") + name)
                .anyMatch(name -> type.getName().equals(name) || type.getName().startsWith(name + "$"));
    }

    @Test
    void registry_and_discovery_preparation_use_owned_ports() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInPackage(c, BASE_PACKAGE + ".schemas.registry")
                || c.getName().equals(BASE_PACKAGE + ".schemas.discovery.DiscoverySourcePreparer"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> schemaOwnershipDependencyForbidden(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void document_extraction_consumes_schema_snapshots_only() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".documents.application.processing").stream()
            .filter(d -> d.getOriginClass().getSimpleName().equals("GraphExtractionService"))
            .filter(d -> isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.registry",
                BASE_PACKAGE + ".schemas.discovery"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void schema_boundary_negative_fixture_rejects_repository_entity_client_and_transaction_bypasses() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenSchemaBoundaryFixture.class)
            .get(ForbiddenSchemaBoundaryFixture.class);
        Set<String> foreign = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> schemaOwnershipDependencyForbidden(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName())
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(foreign.containsAll(Set.of("KnowledgeBaseRepository", "DocumentUploadRepository",
            "JpaKnowledgeBaseSchemaRepository")));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream()
            .anyMatch(d -> schemaContractDependencyForbidden(d.getTargetClass())
                && d.getTargetClass().getSimpleName().equals("SchemaDefinitionEntity")));
        assertTrue(fixture.getMethods().stream().anyMatch(ArchitectureBoundaryTest::isStoreTransactional));
    }


    @Test
    void draft_navigation_consumes_downstream_summary_contracts() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".schemas.drafts").stream()
            .filter(d -> isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.evaluation.adapters",
                BASE_PACKAGE + ".schemas.evaluation.application", BASE_PACKAGE + ".schemas.evaluation.ports",
                BASE_PACKAGE + ".schemas.reprocessing.adapters", BASE_PACKAGE + ".schemas.reprocessing.application",
                BASE_PACKAGE + ".schemas.reprocessing.ports", BASE_PACKAGE + ".schemas.publication.adapters",
                BASE_PACKAGE + ".schemas.publication.application", BASE_PACKAGE + ".schemas.publication.ports")
                || isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.evaluation.domain",
                    BASE_PACKAGE + ".schemas.reprocessing.domain", BASE_PACKAGE + ".schemas.publication.domain")
                    && !d.getTargetClass().isEnum())
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void draft_authoring_uses_public_document_registry_and_knowledge_base_facts() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".schemas.drafts").stream()
            .filter(d -> draftForeignImplementation(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    private static boolean draftForeignImplementation(JavaClass target) {
        return schemaOwnershipDependencyForbidden(target)
            || isInPackage(target, BASE_PACKAGE + ".schemas.registry")
            || Set.of(BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseService", BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseLifecycleService",
                BASE_PACKAGE + ".ai.profiles.domain.AiProfileNode").contains(target.getName());
    }

    @Test
    void draft_fact_boundary_values_are_immutable_and_exclude_persistence_and_clients() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInPackage(c, BASE_PACKAGE + ".schemas.drafts.ports")
                && !c.getSimpleName().endsWith("Repository") && !c.getSimpleName().equals("DraftBinaryStorage"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> draftForeignImplementation(d.getTargetClass()) || isInfrastructureClient(d.getTargetClass())
                || isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.drafts.domain",
                    BASE_PACKAGE + ".schemas.drafts.adapters", BASE_PACKAGE + ".schemas.drafts.application"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(c -> isInPackage(c, BASE_PACKAGE + ".schemas.drafts.ports")
                && !c.getSimpleName().endsWith("Repository"))
            .flatMap(c -> c.getFields().stream())
            .filter(field -> !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL))
            .map(field -> field.getFullName()).forEach(violations::add);
        assertNoViolations(violations);
    }

    @Test
    void draft_boundary_fixture_rejects_foreign_records_repositories_clients_and_adapter_transactions() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenDraftBoundaryFixture.class)
            .get(ForbiddenDraftBoundaryFixture.class);
        Set<String> forbidden = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> draftForeignImplementation(d.getTargetClass()) || isInfrastructureClient(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(forbidden.containsAll(Set.of("SchemaDefinitionRepository", "KnowledgeBaseRepository",
            "DocumentUploadRepository", "AiProfileNode", "ChatModel")));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream()
            .anyMatch(d -> d.getTargetClass().getSimpleName().equals("SchemaDraftRepository")
                && !integrationDependencyAllowed(d.getTargetClass())),
            "Mapping adapters cannot consume authoring persistence ports");
        assertTrue(fixture.getMethods().stream().anyMatch(ArchitectureBoundaryTest::isStoreTransactional));
    }

    private static class ForbiddenDraftBoundaryFixture {
        io.github.vfedoriv.graphrag.schemas.drafts.ports.SchemaDraftRepository drafts;
        io.github.vfedoriv.graphrag.schemas.registry.ports.SchemaDefinitionRepository schemas;
        io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository knowledgeBases;
        io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository documents;
        io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode mutableProfile;
        ChatModel model;

        @RelationalTransactional
        void forbiddenAdapterTransaction() { }
    }

    private static boolean schemaContractDependencyForbidden(JavaClass target) {
        return isInfrastructureClient(target)
            || isInAnyPackage(target, BASE_PACKAGE + ".schemas.registry.adapters",
                BASE_PACKAGE + ".schemas.registry.ports", BASE_PACKAGE + ".schemas.registry.domain",
                BASE_PACKAGE + ".repository", BASE_PACKAGE + ".infrastructure");
    }

    private static boolean schemaOwnershipDependencyForbidden(JavaClass target) {
        return isInAnyPackage(target,
                BASE_PACKAGE + ".knowledgebase.application",
                BASE_PACKAGE + ".knowledgebase.adapters",
                BASE_PACKAGE + ".knowledgebase.domain", BASE_PACKAGE + ".knowledgebase.ports",
                BASE_PACKAGE + ".documents.application",
                BASE_PACKAGE + ".documents.adapters",
                BASE_PACKAGE + ".documents.ports",
                BASE_PACKAGE + ".documents.domain",
                BASE_PACKAGE + ".bootstrap")
            || target.getName().equals(BASE_PACKAGE + ".knowledgebase.ports.KnowledgeBaseRepository")
            || target.getName().equals(BASE_PACKAGE + ".knowledgebase.domain.KnowledgeBaseNode")
            || target.getSimpleName().equals("JpaKnowledgeBaseSchemaRepository");
    }

    private static class ForbiddenSchemaBoundaryFixture {
        io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository knowledgeBases;
        io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository documents;
        io.github.vfedoriv.graphrag.knowledgebase.adapters.relational.repository.JpaKnowledgeBaseSchemaRepository associations;
        io.github.vfedoriv.graphrag.schemas.registry.adapters.relational.entity.SchemaDefinitionEntity persistenceValue;

        @RelationalTransactional
        void newAdapterTransaction() {
        }
    }

    private static boolean pureDocumentRuleDependencyForbidden(JavaClass target) {
        return isInfrastructureClient(target)
            || isInAnyPackage(target, BASE_PACKAGE + ".documents.adapters", BASE_PACKAGE + ".documents.application",
                BASE_PACKAGE + ".documents.api", BASE_PACKAGE + ".documents.ports", BASE_PACKAGE + ".bootstrap",
                BASE_PACKAGE + ".infrastructure", BASE_PACKAGE + ".service", BASE_PACKAGE + ".repository",
                "jakarta.persistence", "org.springframework.data", "java.sql", "java.net.http");
    }

    @Test
    void document_rules_ports_and_workflows_keep_effects_in_adapters() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInPackage(c, BASE_PACKAGE + ".documents"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getOriginClass(), BASE_PACKAGE + ".documents.domain")
                    && pureDocumentRuleDependencyForbidden(d.getTargetClass())
                || isInPackage(d.getOriginClass(), BASE_PACKAGE + ".documents.application")
                    && isInfrastructureClient(d.getTargetClass())
                || isInPackage(d.getOriginClass(), BASE_PACKAGE + ".documents.ports")
                    && (isInfrastructureClient(d.getTargetClass())
                        || isInPackage(d.getTargetClass(), BASE_PACKAGE + ".documents.adapters")))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void document_api_controllers_do_not_access_repositories_or_clients() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".documents.api").stream()
            .filter(d -> d.getOriginClass().isAnnotatedWith(org.springframework.web.bind.annotation.RestController.class))
            .filter(d -> isInfrastructureClient(d.getTargetClass())
                || isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".documents.ports",
                    BASE_PACKAGE + ".documents.adapters", REPOSITORY_PACKAGE))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }


    @Test
    void document_guards_reject_new_foreign_callers_and_pure_rule_effects() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ForbiddenDocumentFixture.class);
        Set<String> edges = documentTransitionalEdges(fixture);
        assertTrue(edges.stream().anyMatch(e -> e.endsWith("documents.ports.DocumentUploadRepository")));
        Set<String> forbidden = fixture.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> pureDocumentRuleDependencyForbidden(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(forbidden.containsAll(Set.of("Neo4jClient", "Path", "Files", "ChatModel", "DocumentProcessingService",
            "DocumentBinaryStorageAdapter", "JdbcTemplate", "EntityManager")));
    }

    private static class ForbiddenDocumentFixture {
        io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository foreignRepository;
        io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingService workflow;
        io.github.vfedoriv.graphrag.documents.adapters.binary.DocumentBinaryStorageAdapter binary;
        Neo4jClient neo4j;
        ChatModel model;
        Path path;
        Files files;
        java.net.http.HttpClient http;
        org.springframework.jdbc.core.JdbcTemplate jdbc;
        jakarta.persistence.EntityManager entityManager;
    }

    @Test
    void processing_receives_stages_from_assembly() {
        java.lang.reflect.Constructor<?> constructor = io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingService.class
            .getConstructors()[0];
        Set<String> parameters = java.util.Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(parameters.containsAll(Set.of("SourceParsingStage", "ChunkPreparationStage", "EmbeddingPersistenceStage",
            "GraphExtractionStage", "ProcessingOptionResolver", "ProcessingJsonCodec")),
            "Processing must receive its stages, not build an alternative workflow");
    }

    @Test
    void documents_use_ai_compatibility_without_legacy_bridges() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".documents").stream()
            .filter(d -> Set.of(SERVICE_PACKAGE + ".EmbeddingSpacePolicy", SERVICE_PACKAGE + ".EmbeddingSpaceIdentity",
                SERVICE_PACKAGE + ".EmbeddingSpace").contains(d.getTargetClass().getName()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void extraction_processing_and_recovery_are_document_owned() {
        assertTrue(io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingService.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.processing"));
        assertTrue(io.github.vfedoriv.graphrag.documents.application.processing.GraphExtractionService.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.processing"));
        assertTrue(io.github.vfedoriv.graphrag.documents.application.processing.DocumentProcessingRecoveryService.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.processing"));
    }

    @Test
    void document_chunk_values_and_ports_do_not_expose_sdn() {
        assertTrue(!io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode.class.isAnnotationPresent(Node.class),
            "SDN mapping belongs in document adapters");
        assertTrue(!Neo4jRepository.class.isAssignableFrom(io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository.class),
            "The owned chunk repository port must not extend SDN");
    }

    @Test
    void processing_rules_and_stages_have_document_ownership() {
        assertTrue(PRODUCTION_CLASSES.get(BASE_PACKAGE + ".documents.domain.processing.ChunkHierarchyBuilder").getPackageName()
            .equals(BASE_PACKAGE + ".documents.domain.processing"));
        assertTrue(io.github.vfedoriv.graphrag.documents.application.processing.ChunkPreparationStage.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.processing"));
        assertTrue(io.github.vfedoriv.graphrag.documents.adapters.parsing.RoutedDocumentParser.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.adapters.parsing"));
    }

    @Test
    void document_entry_points_belong_to_documents_api() {
        assertTrue(io.github.vfedoriv.graphrag.documents.api.DocumentController.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.api"));
        assertTrue(io.github.vfedoriv.graphrag.documents.api.ChunkingStateController.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.api"));
    }

    @Test
    void document_management_uses_owned_binary_effects() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".documents.application.management").stream()
            .filter(d -> isInfrastructureClient(d.getTargetClass())
                || d.getTargetClass().getName().equals(BASE_PACKAGE + ".storage.BinaryStorageService"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void document_management_is_owned_by_documents() {
        assertTrue(io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.management"));
        assertTrue(io.github.vfedoriv.graphrag.documents.application.management.DocumentStorageReconciliationService.class.getPackageName()
            .equals(BASE_PACKAGE + ".documents.application.management"));
    }

    @Test
    void document_relational_adapters_have_owned_scanning_roots() {
        assertTrue(io.github.vfedoriv.graphrag.documents.adapters.relational.RelationalDocumentUploadRepository.class
            .getPackageName().equals(BASE_PACKAGE + ".documents.adapters.relational"));
        assertTrue(io.github.vfedoriv.graphrag.documents.adapters.relational.entity.DocumentUploadEntity.class
            .getPackageName().equals(BASE_PACKAGE + ".documents.adapters.relational.entity"));
    }

    @Test
    void document_records_and_repository_ports_belong_to_documents() {
        Set<Class<?>> records = Set.of(
            io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode.class,
            io.github.vfedoriv.graphrag.documents.domain.DocumentProcessingRunNode.class,
            io.github.vfedoriv.graphrag.documents.domain.ExtractionRunNode.class,
            io.github.vfedoriv.graphrag.documents.domain.DocumentStorageMutationNode.class);
        assertTrue(records.stream().allMatch(c -> c.getPackageName().equals(BASE_PACKAGE + ".documents.domain")),
            "Document operational records must be owned by documents");
        Set<Class<?>> ports = Set.of(
            io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository.class,
            io.github.vfedoriv.graphrag.documents.ports.DocumentProcessingRunRepository.class,
            io.github.vfedoriv.graphrag.documents.ports.ExtractionRunRepository.class,
            io.github.vfedoriv.graphrag.documents.ports.DocumentStorageMutationRepository.class);
        assertTrue(ports.stream().allMatch(c -> c.getPackageName().equals(BASE_PACKAGE + ".documents.ports")),
            "Document operational repositories must be owned ports");
    }

    @Test
    void controllers_do_not_depend_directly_on_repositories_or_neo4j_client() {
        Set<String> violations = dependenciesFromClassesIn(CONTROLLER_PACKAGE).stream()
                .filter(dependency -> isInPackage(dependency.getTargetClass(), REPOSITORY_PACKAGE)
                        || dependency.getTargetClass().isAssignableTo(Neo4jClient.class))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void domain_classes_do_not_depend_on_adapters_services_dtos_repositories_or_workflows() {
        Set<String> violations = dependenciesFromClassesIn(DOMAIN_PACKAGE).stream()
                .filter(dependency -> isInAnyPackage(
                        dependency.getTargetClass(),
                        CONTROLLER_PACKAGE,
                        DTO_PACKAGE,
                        REPOSITORY_PACKAGE,
                        SERVICE_PACKAGE,
                        WORKFLOW_PACKAGE))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void feature_and_application_code_do_not_depend_on_controllers() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
                .filter(javaClass -> !isInPackage(javaClass, CONTROLLER_PACKAGE))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isInPackage(dependency.getTargetClass(), CONTROLLER_PACKAGE))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void direct_neo4j_client_usage_stays_in_graph_adapters_or_assembly() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
                .filter(javaClass -> !isInAnyPackage(javaClass, BASE_PACKAGE + ".documents.adapters.graph", BASE_PACKAGE + ".search.retrieval.adapters.graph", BASE_PACKAGE + ".indexes.adapters.graph", BASE_PACKAGE + ".bootstrap"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().isAssignableTo(Neo4jClient.class))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void application_workflows_do_not_access_infrastructure_clients_directly() {
        Set<String> violations = dependenciesFromClassesIn(APPLICATION_PACKAGE).stream()
                .filter(dependency -> dependency.getTargetClass().isAssignableTo(Neo4jClient.class)
                        || dependency.getTargetClass().isAssignableTo(ChatModel.class)
                        || dependency.getTargetClass().isAssignableTo(EmbeddingModel.class)
                        || dependency.getTargetClass().isAssignableTo(Path.class)
                        || dependency.getTargetClass().isAssignableTo(Files.class))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void persistence_transactions_are_store_qualified() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
                .filter(javaClass -> !isInPackage(javaClass, TRANSACTION_ANNOTATION_PACKAGE))
                .filter(javaClass -> javaClass.isAnnotatedWith(Transactional.class)
                        || javaClass.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(Transactional.class)))
                .map(JavaClass::getName)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void repository_and_entity_ownership_stays_disjoint() {
        Set<String> violations = new TreeSet<>();
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(JpaRepository.class))
                .filter(javaClass -> !isInAnyPackage(javaClass, BASE_PACKAGE + ".ai.profiles.adapters.relational.repository", BASE_PACKAGE + ".settings.adapters.relational.repository", BASE_PACKAGE + ".documents.adapters.relational.repository",
                    BASE_PACKAGE + ".knowledgebase.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.registry.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.drafts.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.evaluation.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.publication.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.reprocessing.adapters.relational.repository",
                    BASE_PACKAGE + ".search.runs.adapters.relational.repository"))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Neo4jRepository.class))
                .filter(javaClass -> !isInAnyPackage(javaClass, REPOSITORY_PACKAGE, BASE_PACKAGE + ".documents.adapters.graph.repository"))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .filter(javaClass -> !isInAnyPackage(javaClass, BASE_PACKAGE + ".ai.profiles.adapters.relational.entity", BASE_PACKAGE + ".settings.adapters.relational.entity", BASE_PACKAGE + ".documents.adapters.relational.entity",
                    BASE_PACKAGE + ".knowledgebase.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.registry.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.drafts.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.evaluation.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.publication.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.reprocessing.adapters.relational.entity",
                    BASE_PACKAGE + ".search.runs.adapters.relational.entity"))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> isInPackage(javaClass, RELATIONAL_ENTITY_PACKAGE))
                .filter(javaClass -> javaClass.isAnnotatedWith(Node.class))
                .map(JavaClass::getName)
                .forEach(violations::add);

        assertNoViolations(violations);
    }

    @Test
    void only_graph_native_sdn_nodes_and_repositories_remain() {
        Set<String> violations = new TreeSet<>();
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Node.class))
                .map(JavaClass::getName)
                .filter(className -> !ALLOWED_SDN_NODE_TYPES.contains(className))
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Neo4jRepository.class))
                .map(JavaClass::getName)
                .filter(className -> !ALLOWED_NEO4J_REPOSITORIES.contains(className))
                .forEach(violations::add);

        assertNoViolations(violations);
    }

    @Test
    void transactional_methods_are_not_called_through_self_invocation() {
        Set<String> violations = new TreeSet<>();
        Set<String> observed = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION_CLASSES) {
            for (JavaMethodCall methodCall : javaClass.getMethodCallsFromSelf()) {
                if (!methodCall.getOriginOwner().equals(methodCall.getTargetOwner())) {
                    continue;
                }
                Optional<JavaMethod> targetMethod = methodCall.getTarget().resolveMember();
                if (targetMethod.isPresent() && isStoreTransactional(targetMethod.get())) {
                    observed.add(methodCall.getOrigin().getFullName() + " -> " + methodCall.getTarget().getFullName());
                }
                if (targetMethod.isPresent() && isStoreTransactional(targetMethod.get())
                        && !EXISTING_TRANSACTION_SELF_CALLS.contains(methodCall.getOrigin().getFullName()
                            + " -> " + methodCall.getTarget().getFullName())) {
                    violations.add(methodCall.getDescription());
                }
            }
        }

        Set<String> stale = new TreeSet<>(EXISTING_TRANSACTION_SELF_CALLS);
        stale.removeAll(observed);
        violations.addAll(stale);
        assertNoViolations(violations);
    }

    @Test
    void reprocessing_recovery_and_execution_do_not_access_document_implementation() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(javaClass -> javaClass.getName().equals(SERVICE_PACKAGE + ".SchemaReprocessingRecoveryService")
                || isInPackage(javaClass, BASE_PACKAGE + ".schemas.reprocessing.application"))
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> isDocumentImplementation(dependency.getTargetClass())
                || isInfrastructureClient(dependency.getTargetClass()))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void reprocessing_boundary_values_are_free_of_entities_clients_and_runtime_context() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(javaClass -> isInAnyPackage(javaClass, BASE_PACKAGE + ".schemas.reprocessing.ports",
                BASE_PACKAGE + ".documents.contracts"))
            .filter(javaClass -> !Set.of("SchemaReprocessingItemRepository", "SchemaReprocessingPlanRepository").contains(javaClass.getSimpleName()))
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> !dependency.getTargetClass().getName().startsWith("java.")
                && !dependency.getTargetClass().getName().equals("[B")
                && !dependency.getTargetClass().getPackageName().equals(dependency.getOriginClass().getPackageName())
                && !isInPackage(dependency.getTargetClass(), BASE_PACKAGE + ".schemas.contracts"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void reprocessing_integration_only_maps_public_contracts() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".bootstrap.integration.reprocessing").stream()
            .filter(dependency -> isInfrastructureClient(dependency.getTargetClass())
                || isInPackage(dependency.getTargetClass(), BASE_PACKAGE)
                    && !isInAnyPackage(dependency.getTargetClass(),
                        BASE_PACKAGE + ".schemas.reprocessing.ports", BASE_PACKAGE + ".documents.contracts",
                        BASE_PACKAGE + ".knowledgebase.contracts", BASE_PACKAGE + ".bootstrap.integration.reprocessing"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void execution_collaborator_uses_only_its_port_and_has_no_transaction() {
        Set<String> violations = PRODUCTION_CLASSES.get(BASE_PACKAGE + ".schemas.reprocessing.application.ReprocessingItemExecution").getDirectDependenciesFromSelf().stream()
            .filter(dependency -> isInfrastructureClient(dependency.getTargetClass())
                || isInPackage(dependency.getTargetClass(), BASE_PACKAGE)
                    && !isInAnyPackage(dependency.getTargetClass(),
                        BASE_PACKAGE + ".schemas.reprocessing.ports", BASE_PACKAGE + ".schemas.reprocessing.application"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(javaClass -> javaClass.getName().equals(BASE_PACKAGE + ".schemas.reprocessing.application.ReprocessingItemExecution")
                || isInPackage(javaClass, BASE_PACKAGE + ".bootstrap.integration.reprocessing")
                || Set.of(BASE_PACKAGE + ".documents.application.processing.DocumentReprocessingFacade",
                    BASE_PACKAGE + ".documents.application.processing.DocumentMigrationPreparationFacade",
                    BASE_PACKAGE + ".documents.application.processing.DocumentProcessingOutcomesFacade")
                    .contains(javaClass.getName()))
            .filter(javaClass -> javaClass.isAnnotatedWith(RelationalTransactional.class)
                || javaClass.isAnnotatedWith(GraphTransactional.class)
                || javaClass.getMethods().stream().anyMatch(ArchitectureBoundaryTest::isStoreTransactional))
            .map(JavaClass::getName).forEach(violations::add);
        assertNoViolations(violations);
    }

    @Test
    void features_do_not_depend_on_reprocessing_assembly() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(javaClass -> !isInPackage(javaClass, BASE_PACKAGE + ".bootstrap"))
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> isInPackage(dependency.getTargetClass(), BASE_PACKAGE + ".bootstrap.integration.reprocessing"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void all_reprocessing_preparation_and_inspection_use_consumer_ports() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(javaClass -> javaClass.getName().startsWith(SERVICE_PACKAGE + ".SchemaReprocessing")
                || isInPackage(javaClass, BASE_PACKAGE + ".schemas.reprocessing"))
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> isDocumentImplementation(dependency.getTargetClass()))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void orchestrator_execution_methods_cannot_reuse_preparation_dependencies() {
        JavaClasses orchestrator = new ClassFileImporter().importClasses(
            io.github.vfedoriv.graphrag.schemas.reprocessing.application.SchemaReprocessingPlanService.class);
        assertNoViolations(documentAccessesFromExecutionMethods(orchestrator));
    }

    @Test
    void execution_method_guard_rejects_a_preparation_repository_access() {
        JavaClasses forbidden = new ClassFileImporter().importClasses(ForbiddenExecutionFixture.class);
        Set<String> violations = documentAccessesFromExecutionMethods(forbidden);
        assertTrue(violations.stream().anyMatch(value -> value.contains("DocumentUploadRepository")),
            "The method-origin guard must reject repository access even when its class edge is allowed for preparation");
    }

    @Test
    void preparation_guard_rejects_repository_and_provider_contract_bypasses() {
        JavaClasses forbidden = new ClassFileImporter().importClasses(ForbiddenPreparationFixture.class);
        Set<String> violations = forbidden.stream()
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> isDocumentImplementation(dependency.getTargetClass()))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertTrue(violations.stream().anyMatch(value -> value.contains("DocumentUploadRepository")));
        assertTrue(violations.stream().anyMatch(value -> value.contains("DocumentMigrationPreparation")));
    }

    private static class ForbiddenPreparationFixture {
        private io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository documents;
        private io.github.vfedoriv.graphrag.documents.contracts.DocumentMigrationPreparation provider;

        void prepare() {
            documents.findById("document");
            provider.allOwned("kb");
        }
    }

    private static Set<String> documentAccessesFromExecutionMethods(JavaClasses classes) {
        return classes.stream()
            .flatMap(javaClass -> javaClass.getAccessesFromSelf().stream())
            .filter(access -> Set.of("processItem", "executionTarget").contains(access.getOrigin().getName()))
            .filter(access -> isDocumentImplementation(access.getTargetOwner()))
            .map(access -> access.getDescription())
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }

    private static class ForbiddenExecutionFixture {
        private io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository documents;

        void processItem() {
            documents.findById("document");
        }
    }

    @Test
    void migrated_knowledge_base_and_ai_paths_cannot_access_foreign_state() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> Set.of(BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseService", BASE_PACKAGE + ".ai.profiles.application.AiProfileService",
                SERVICE_PACKAGE + ".EmbeddingSpacePolicy").contains(c.getName())
                || isInPackage(c, BASE_PACKAGE + ".ai"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> migratedDependencyForbidden(d.getOriginClass().getName(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(c -> c.getName().equals(INFRASTRUCTURE_PERSISTENCE_PACKAGE + ".relational.RelationalAiProfileRepository"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> d.getTargetClass().getSimpleName().contains("KnowledgeBase"))
            .map(ArchitectureBoundaryTest::format).forEach(violations::add);
        assertNoViolations(violations);
    }

    private static boolean migratedDependencyForbidden(String origin, JavaClass target) {
        boolean tokenizerValueBridge = Set.of(BASE_PACKAGE + ".ai.profiles.application.AiProfileService", BASE_PACKAGE + ".knowledgebase.application.KnowledgeBaseService")
            .contains(origin) && target.getName().equals(BASE_PACKAGE + ".ai.domain.TokenizerId");
        return isDocumentImplementation(target) && !tokenizerValueBridge
            || target.getName().equals(SERVICE_PACKAGE + ".GraphArtifactCleanupService")
            || (origin.equals(BASE_PACKAGE + ".ai.profiles.application.AiProfileService") || origin.startsWith(BASE_PACKAGE + ".ai."))
                && (isInPackage(target, BASE_PACKAGE + ".knowledgebase")
                    || target.getSimpleName().contains("KnowledgeBase"));
    }


    @Test
    void ai_rules_ports_and_provider_contracts_are_pure_immutable_values() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInAnyPackage(c, BASE_PACKAGE + ".ai.domain", BASE_PACKAGE + ".ai.ports",
                BASE_PACKAGE + ".knowledgebase.contracts"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> !boundaryValueDependencyAllowed(d.getOriginClass(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(c -> isInAnyPackage(c, BASE_PACKAGE + ".ai.domain", BASE_PACKAGE + ".documents.contracts",
                BASE_PACKAGE + ".knowledgebase.contracts"))
            .flatMap(c -> c.getFields().stream())
            .filter(f -> !f.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL))
            .map(f -> f.getFullName()).forEach(violations::add);
        assertNoViolations(violations);
    }

    private static boolean boundaryValueDependencyAllowed(JavaClass origin, JavaClass target) {
        return !isInfrastructureClient(target) && !isInAnyPackage(target, "java.sql", "java.net.http")
            && (target.getName().startsWith("java.") || target.getPackageName().equals(origin.getPackageName())
                || isInPackage(target, BASE_PACKAGE + ".ai.contracts")
                || isInPackage(origin, BASE_PACKAGE + ".knowledgebase.ports")
                    && isInPackage(target, BASE_PACKAGE + ".knowledgebase.contracts")
                || origin.getPackageName().equals(BASE_PACKAGE + ".ai.ports") && isAiBoundaryValue(target));
    }

    @Test
    void migrated_integration_adapters_use_public_contracts_without_transactions() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(ArchitectureBoundaryTest::isMigratedAdapter)
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> !integrationDependencyAllowed(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(c -> isMigratedAdapter(c) || isInAnyPackage(c,
                BASE_PACKAGE + ".documents.application.inspection", BASE_PACKAGE + ".documents.application.lifecycle",
                BASE_PACKAGE + ".knowledgebase.application") && !Set.of("KnowledgeBaseService", "KnowledgeBaseLifecycleService").contains(c.getSimpleName()))
            .filter(c -> c.isAnnotatedWith(RelationalTransactional.class) || c.isAnnotatedWith(GraphTransactional.class)
                || c.getMethods().stream().anyMatch(ArchitectureBoundaryTest::isStoreTransactional))
            .map(JavaClass::getName).forEach(violations::add);
        assertNoViolations(violations);
    }

    private static boolean isMigratedAdapter(JavaClass c) {
        return isInAnyPackage(c, BASE_PACKAGE + ".bootstrap.integration.ai",
            BASE_PACKAGE + ".bootstrap.integration.knowledgebase", BASE_PACKAGE + ".bootstrap.integration.schemas");
    }

    private static boolean integrationDependencyAllowed(JavaClass target) {
        return isInAnyPackage(target, "java.lang", "java.util", "org.springframework.stereotype",
                "org.springframework.beans.factory.annotation")
            || target.getName().equals("java.io.IOException")
            || target.getName().startsWith(BASE_PACKAGE + ".schemas.evaluation.domain.EvaluationObservations$")
            || isAiBoundaryValue(target)
            || isInPackage(target, BASE_PACKAGE + ".http.contracts")
            || isDraftFactPort(target)
            || isInAnyPackage(target, BASE_PACKAGE + ".ai.ports",
                BASE_PACKAGE + ".documents.contracts", BASE_PACKAGE + ".schemas.registry.ports",
                BASE_PACKAGE + ".schemas.discovery.ports", BASE_PACKAGE + ".schemas.generation.ports", BASE_PACKAGE + ".schemas.evaluation.ports",
                BASE_PACKAGE + ".schemas.evaluation.contracts", BASE_PACKAGE + ".schemas.publication.contracts",
                BASE_PACKAGE + ".schemas.reprocessing.contracts", BASE_PACKAGE + ".schemas.drafts.contracts",
                BASE_PACKAGE + ".schemas.contracts", BASE_PACKAGE + ".knowledgebase.ports",
                BASE_PACKAGE + ".knowledgebase.contracts") || isMigratedAdapter(target);
    }

    private static boolean isDraftFactPort(JavaClass target) {
        return Set.of("DraftDocumentInputs", "DraftSchemaLookup", "DraftKnowledgeBases", "DraftEvaluationSummaries", "DraftReprocessingSummaries").stream()
            .map(name -> BASE_PACKAGE + ".schemas.drafts.ports." + name)
            .anyMatch(name -> target.getName().equals(name) || target.getName().startsWith(name + "$"));
    }

    private static boolean isAiBoundaryValue(JavaClass target) {
        return Set.of(BASE_PACKAGE + ".ai.domain.EmbeddingTarget", BASE_PACKAGE + ".ai.domain.StoredEmbeddingObservation")
            .contains(target.getName());
    }

    @Test
    void features_never_depend_on_bootstrap_or_removed_compatibility_bridge() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> !isInPackage(c, BASE_PACKAGE + ".bootstrap"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE + ".bootstrap")
                || !bridgeCallerAllowed(d.getOriginClass(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    private static boolean bridgeCallerAllowed(JavaClass origin, JavaClass target) {
        return !target.getName().equals(SERVICE_PACKAGE + ".EmbeddingSpacePolicy");
    }


    private static boolean identityDependencyAllowed(JavaClass target) {
        return isInAnyPackage(target, "java.lang", "java.util", "java.security", "java.nio.charset")
            || Set.of(BASE_PACKAGE + ".ai.domain.EmbeddingSpace", BASE_PACKAGE + ".ai.contracts.ProfileFacts",
                BASE_PACKAGE + ".ai.domain.EmbeddingTarget", BASE_PACKAGE + ".ai.domain.TokenizerId").contains(target.getName());
    }

    @Test
    void negative_fixtures_reject_foreign_state_provider_bypasses_and_new_bridge_callers() {
        JavaClass forbidden = new ClassFileImporter().importClasses(ForbiddenAiStateFixture.class)
            .get(ForbiddenAiStateFixture.class);
        Set<String> foreign = forbidden.getDirectDependenciesFromSelf().stream()
            .filter(d -> migratedDependencyForbidden(BASE_PACKAGE + ".ai.profiles.application.AiProfileService", d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertTrue(foreign.stream().anyMatch(v -> v.contains("DocumentChunkRepository")));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d -> isInfrastructureClient(d.getTargetClass())));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d -> !identityDependencyAllowed(d.getTargetClass())));
        assertTrue(foreign.stream().anyMatch(v -> v.contains("KnowledgeBaseRepository")));
        assertTrue(foreign.stream().anyMatch(v -> v.contains("KnowledgeBaseNode")));
        assertTrue(foreign.stream().anyMatch(v -> v.contains("StoredEmbeddings")));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            !integrationDependencyAllowed(d.getTargetClass()) && d.getTargetClass().getSimpleName().equals("DocumentChunkRepository")));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            !boundaryValueDependencyAllowed(forbidden, d.getTargetClass()) && d.getTargetClass().getSimpleName().equals("AiProfileNode")));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            isInPackage(d.getTargetClass(), BASE_PACKAGE + ".bootstrap")));
        assertTrue(PRODUCTION_CLASSES.stream().noneMatch(c -> c.getName().equals(SERVICE_PACKAGE + ".EmbeddingSpacePolicy")));
    }

    @Test
    void migrated_guards_reject_ai_assignment_bypasses_and_relational_adapter_clients() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenAiStateFixture.class).get(ForbiddenAiStateFixture.class);
        Set<String> forbidden = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> migratedDependencyForbidden(BASE_PACKAGE + ".ai.profiles.application.AiProfileService", d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(forbidden.containsAll(Set.of("KnowledgeBaseRepository", "KnowledgeBaseNode", "AiProfileAssignments")));
        Set<String> adapterForbidden = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> !integrationDependencyAllowed(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(adapterForbidden.containsAll(Set.of("JdbcTemplate", "EntityManager", "DataSource", "Connection")));
        assertTrue(adapterForbidden.contains("EmbeddingCompatibilityRule"), "Mapping adapters may use values, not own compatibility decisions");
    }

    private static class ForbiddenAiStateFixture {
        io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository chunks;
        io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository assignments;
        io.github.vfedoriv.graphrag.knowledgebase.domain.KnowledgeBaseNode knowledgeBase;
        io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode profile;
        io.github.vfedoriv.graphrag.documents.contracts.StoredEmbeddings provider;
        io.github.vfedoriv.graphrag.bootstrap.integration.ai.StoredEmbeddingInformationAdapter adapter;
        io.github.vfedoriv.graphrag.search.runs.ports.SearchProfiles newSearchProfileCaller;
        io.github.vfedoriv.graphrag.ai.domain.EmbeddingCompatibilityRule adapterDecisionBypass;
        io.github.vfedoriv.graphrag.knowledgebase.contracts.AiProfileAssignments assignmentProvider;
        org.springframework.jdbc.core.JdbcTemplate jdbc;
        jakarta.persistence.EntityManager entities;
        javax.sql.DataSource dataSource;
        java.sql.Connection connection;
    }

    private static boolean isDocumentImplementation(JavaClass target) {
        return isInAnyPackage(target, BASE_PACKAGE + ".document", BASE_PACKAGE + ".documents",
                APPLICATION_PACKAGE + ".processing")
            || isInPackage(target, INFRASTRUCTURE_PERSISTENCE_PACKAGE) && target.getSimpleName().contains("Document")
            || target.getName().startsWith(DOMAIN_PACKAGE + ".Document")
            || target.getName().startsWith(REPOSITORY_PACKAGE + ".Document")
            || target.getName().startsWith(SERVICE_PACKAGE + ".Document")
            || target.getName().equals(SERVICE_PACKAGE + ".ImmutableDocumentProcessingInput");
    }

    private static boolean isInfrastructureClient(JavaClass target) {
        return target.isAssignableTo(Neo4jClient.class)
            || target.isAssignableTo(ChatModel.class)
            || target.isAssignableTo(EmbeddingModel.class)
            || target.isAssignableTo(Path.class)
            || target.isAssignableTo(Files.class)
            || target.isAssignableTo(java.io.File.class)
            || target.isAssignableTo(jakarta.persistence.EntityManager.class)
            || target.isAssignableTo(org.springframework.jdbc.core.JdbcTemplate.class)
            || target.isAssignableTo(javax.sql.DataSource.class)
            || target.isAssignableTo(java.sql.Connection.class);
    }

    private static boolean isStoreTransactional(JavaMethod method) {
        return method.isAnnotatedWith(GraphTransactional.class)
                || method.isAnnotatedWith(RelationalTransactional.class)
                || method.getOwner().isAnnotatedWith(GraphTransactional.class)
                || method.getOwner().isAnnotatedWith(RelationalTransactional.class);
    }

    private Set<Dependency> dependenciesFromClassesIn(String packageName) {
        Set<Dependency> dependencies = new TreeSet<>(Comparator.comparing(ArchitectureBoundaryTest::format));
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> isInPackage(javaClass, packageName))
                .forEach(javaClass -> dependencies.addAll(javaClass.getDirectDependenciesFromSelf()));
        return dependencies;
    }

    private static boolean isInAnyPackage(JavaClass javaClass, String... packageNames) {
        for (String packageName : packageNames) {
            if (isInPackage(javaClass, packageName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInPackage(JavaClass javaClass, String packageName) {
        String classPackageName = javaClass.getPackageName();
        return classPackageName.equals(packageName) || classPackageName.startsWith(packageName + ".");
    }

    private static String format(Dependency dependency) {
        return dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName();
    }

    private static void assertNoViolations(Set<String> violations) {
        assertTrue(violations.isEmpty(), "Architecture boundary violations:\n" + String.join("\n", violations));
    }
    private static final String SEARCH_PACKAGE = BASE_PACKAGE + ".search";

    @Test
    void search_owns_workflows_effects_and_persistence() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> !isInPackage(c, SEARCH_PACKAGE))
            .filter(c -> c.getSimpleName().matches("(AdvancedSearch.*(Node|Repository|Controller|Service|Processor|Codec)|Cypher.*Service|QueryController|QueryAskService|Neo4j(GraphRetrieval|TextChunkRetrieval|ParentContext).*Repository|RelationalAdvancedSearch.*Repository|JpaAdvancedSearch.*Repository)"))
            .map(JavaClass::getName).collect(java.util.stream.Collectors.toSet());
        assertNoViolations(violations);
        assertTrue(PRODUCTION_CLASSES.stream().noneMatch(c -> c.getName().equals(SERVICE_PACKAGE + ".EmbeddingSpacePolicy")));
    }

    private static boolean searchForeignInternal(JavaClass target) {
        return isInAnyPackage(target, BASE_PACKAGE + ".documents", BASE_PACKAGE + ".schemas", BASE_PACKAGE + ".knowledgebase")
            && !isInAnyPackage(target, BASE_PACKAGE + ".documents.contracts", BASE_PACKAGE + ".schemas.contracts", BASE_PACKAGE + ".knowledgebase.contracts")
            || Set.of(REPOSITORY_PACKAGE + ".KnowledgeBaseRepository", DOMAIN_PACKAGE + ".KnowledgeBaseNode").contains(target.getName());
    }

    private static boolean searchPureEffect(JavaClass target) {
        return isInfrastructureClient(target)
            || isInAnyPackage(target, "org.neo4j.driver", "org.springframework.ai", "java.nio.file", "java.net.http", "java.sql", "jakarta.persistence")
            || isInPackage(target, BASE_PACKAGE + ".bootstrap")
            || target.isInterface() && isInAnyPackage(target, BASE_PACKAGE + ".schemas.contracts", BASE_PACKAGE + ".documents.contracts", BASE_PACKAGE + ".knowledgebase.contracts")
            || isInPackage(target, SEARCH_PACKAGE) && (target.getPackageName().contains(".adapters")
                || target.getPackageName().contains(".application") || target.getPackageName().contains(".api")
                || target.isInterface() && target.getPackageName().contains(".ports"));
    }

    @Test
    void search_uses_public_foreign_contracts_and_keeps_effects_in_adapters() {
        Set<String> violations = dependenciesFromClassesIn(SEARCH_PACKAGE).stream()
            .filter(d -> searchForeignInternal(d.getTargetClass())
                || d.getOriginClass().getPackageName().contains(".domain") && searchPureEffect(d.getTargetClass())
                || d.getOriginClass().getPackageName().contains(".application") && (isInfrastructureClient(d.getTargetClass()) || isInAnyPackage(d.getTargetClass(), "org.neo4j.driver", "org.springframework.ai"))
                || d.getOriginClass().getPackageName().contains(".ports")
                    && (isInfrastructureClient(d.getTargetClass()) || d.getTargetClass().getPackageName().contains(".adapters"))
                || !d.getOriginClass().getPackageName().contains(".adapters.model")
                    && d.getTargetClass().getName().equals(BASE_PACKAGE + ".ai.profiles.domain.AiProfileNode"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void foreign_features_do_not_depend_on_search_implementations() throws java.io.IOException {
        Set<String> actual = PRODUCTION_CLASSES.stream()
            .filter(c -> !isInPackage(c, SEARCH_PACKAGE) && !isInPackage(c, BASE_PACKAGE + ".bootstrap"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getTargetClass(), SEARCH_PACKAGE))
            .map(ArchitectureBoundaryTest::format).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        assertNoViolations(actual);
        assertNoViolations(dependenciesFromClassesIn(BASE_PACKAGE + ".documents").stream()
            .filter(d -> isInPackage(d.getTargetClass(), SEARCH_PACKAGE))
            .map(ArchitectureBoundaryTest::format).collect(java.util.stream.Collectors.toSet()));
    }

    private static boolean searchMappingDependencyAllowed(JavaClass target) {
        return !isInfrastructureClient(target)
            && !isInAnyPackage(target, "org.neo4j.driver", "org.springframework.ai", "org.springframework.transaction", "java.sql", "java.nio.file", "java.net.http", "jakarta.persistence", "org.springframework.jdbc")
            && (!isInPackage(target, BASE_PACKAGE)
            || isInAnyPackage(target, BASE_PACKAGE + ".documents.contracts", BASE_PACKAGE + ".schemas.contracts",
                BASE_PACKAGE + ".knowledgebase.contracts", BASE_PACKAGE + ".bootstrap.integration.search")
            || target.getName().equals(BASE_PACKAGE + ".http.contracts.NotFoundException")
            || isInPackage(target, SEARCH_PACKAGE) && target.getPackageName().contains(".ports"));
    }

    @Test
    void search_mapping_adapters_are_transaction_free_and_contract_only() {
        JavaClasses bridges = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE + ".bootstrap.integration.search");
        Set<String> violations = bridges.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> !searchMappingDependencyAllowed(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(java.util.stream.Collectors.toSet());
        assertNoViolations(violations);
        for (JavaClass bridge : bridges) {
            assertTrue(!bridge.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class));
            assertTrue(bridge.getMethods().stream().noneMatch(m -> m.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                || m.isAnnotatedWith(io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional.class)));
        }
    }


    @Test
    void search_negative_fixtures_reject_foreign_state_effects_and_new_support_seams() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenSearchFixture.class).get(ForbiddenSearchFixture.class);
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d -> searchForeignInternal(d.getTargetClass())));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d -> searchPureEffect(d.getTargetClass())));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE + ".bootstrap")));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d -> isInPackage(d.getTargetClass(), SEARCH_PACKAGE)
            && d.getTargetClass().getPackageName().contains(".adapters.relational")));
    }
    @Test
    void search_governance_rejects_effect_ports_and_external_bridge_effects() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenSearchFixture.class).get(ForbiddenSearchFixture.class);
        Set<String> pureEffects = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> searchPureEffect(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(pureEffects.containsAll(Set.of("QueryExecutor", "SearchEmbeddingModel", "AdvancedSearchRunRepository", "SchemaSnapshots")));
        Set<String> bridgeEffects = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> !searchMappingDependencyAllowed(d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(bridgeEffects.containsAll(Set.of("JdbcTemplate", "EntityManager", "Driver", "ChatModel", "Files", "TransactionTemplate", "HttpClient")));
    }
    private static class ForbiddenSearchFixture {
        io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository foreign;
        io.github.vfedoriv.graphrag.search.runs.adapters.relational.entity.AdvancedSearchRunEntity run;
        io.github.vfedoriv.graphrag.search.query.adapters.graph.QueryNeo4jExecutor database;
        io.github.vfedoriv.graphrag.bootstrap.integration.search.SearchSchemaAdapter assembly;
        ChatModel provider;
        io.github.vfedoriv.graphrag.search.query.ports.QueryExecutor queryPort;
        io.github.vfedoriv.graphrag.search.retrieval.ports.SearchEmbeddingModel embeddings;
        io.github.vfedoriv.graphrag.search.runs.ports.AdvancedSearchRunRepository persistence;
        io.github.vfedoriv.graphrag.schemas.contracts.SchemaSnapshots schemaCapability;
        org.springframework.jdbc.core.JdbcTemplate jdbc;
        jakarta.persistence.EntityManager entities;
        org.neo4j.driver.Driver driver;
        Files files;
        java.net.http.HttpClient http;
        org.springframework.transaction.support.TransactionTemplate transactions;
    }
}
