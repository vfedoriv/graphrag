package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.schemas.discovery.DiscoverySourcePreparer;

import io.github.vfedoriv.graphrag.schemas.registry.application.SchemaRegistryService;
import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaResolver;
import io.github.vfedoriv.graphrag.schemas.registry.application.ActiveSchemaContext;
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

    private static final Set<String> FROZEN_LEGACY_NEO4J_CLIENT_EXCEPTIONS = Set.of(
            BASE_PACKAGE + ".config.PersistenceConfiguration",
            BASE_PACKAGE + ".service.EmbeddingSpaceIndexService"
    );
    private static final Set<String> FROZEN_LEGACY_TRANSACTIONAL_SELF_INVOCATION_EXCEPTIONS = Set.of(
            BASE_PACKAGE + ".service.AiProfileService",
            BASE_PACKAGE + ".service.KnowledgeBaseService",
            BASE_PACKAGE + ".service.RuntimeSettingsService",
            BASE_PACKAGE + ".schemas.drafts.application.SchemaDraftLifecycleService",
            BASE_PACKAGE + ".schemas.publication.application.SchemaDraftPublicationService",
            BASE_PACKAGE + ".schemas.drafts.application.SchemaDraftReviewService",
            BASE_PACKAGE + ".schemas.drafts.application.SchemaDraftSourceService",
            BASE_PACKAGE + ".schemas.registry.application.SchemaRegistryService"
    );

    // Exact direct dependencies retained until roadmap steps 8–9. New callers fail this check.
    private static final java.util.Map<Integer, Set<String>> FROZEN_SCHEMA_BRIDGE_EDGES = java.util.Map.of(
        8, Set.of(
            edge("service.AdvancedSearchGraphRetriever", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.AdvancedSearchPlanValidator", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.AdvancedSearchPlanner", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.AdvancedSearchReadinessService", "schemas.registry.ports.SchemaDefinitionRepository"),
            edge("service.AdvancedSearchRunService", "schemas.registry.domain.SchemaDefinitionNode"),
            edge("service.AdvancedSearchRunService", "schemas.registry.ports.SchemaDefinitionRepository"),
            edge("service.CypherGenerationService", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.CypherGenerationService", "schemas.registry.application.ActiveSchemaResolver"),
            edge("service.CypherValidationService", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.CypherValidationService", "schemas.registry.application.ActiveSchemaResolver"),
            edge("service.DefaultAdvancedSearchRunProcessor", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.DefaultAdvancedSearchRunProcessor", "schemas.registry.application.SchemaParser"),
            edge("service.GraphPlanCypherRenderer", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.GraphPlanValidationService", "schemas.registry.application.ActiveSchemaContext"),
            edge("service.GraphPlanValidationService", "schemas.registry.application.ActiveSchemaResolver"),
            edge("service.GraphPlanValidationService$ValidatedGraphPlan", "schemas.registry.application.ActiveSchemaContext")
        ),
        9, Set.of(
            edge("config.PersistenceConfiguration", "schemas.registry.adapters.relational.entity.SchemaDefinitionEntity"),
            edge("config.PersistenceConfiguration", "schemas.registry.adapters.relational.repository.JpaSchemaDefinitionRepository"),
            edge("controller.SchemaController", "schemas.discovery.application.SchemaDiscoveryService"),
            edge("controller.SchemaController", "schemas.registry.application.SchemaRegistryService"),
            edge("controller.SchemaController", "schemas.registry.domain.SchemaDefinitionNode"),
            edge("dto.SchemaDiscoveryResponse", "schemas.discovery.DiscoveryContracts$Candidate"),
            edge("dto.SchemaDiscoveryResponse", "schemas.discovery.DiscoveryContracts$Conflict"),
            edge("dto.SchemaDiscoveryResponse", "schemas.discovery.DiscoveryContracts$ResponseStatus"),
            edge("dto.SchemaDiscoveryResponse$SourceOutcome", "schemas.discovery.DiscoveryContracts$FailureCategory"),
            edge("dto.SchemaDiscoveryResponse$SourceOutcome", "schemas.discovery.DiscoveryContracts$SourceStatus"),
            edge("dto.SchemaDiscoveryResponse$SourceOutcome", "schemas.discovery.DiscoveryContracts$SourceType"),
            edge("dto.SchemaDiscoveryResponse$SourceOutcome", "schemas.discovery.SourceFailureCode"),
            edge("service.SchemaBootstrapService", "schemas.registry.application.SchemaRegistryService")
        )
    );

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE);

    // Exact transitional edges, grouped by their retirement roadmap step.
    // 7 evaluation; 8 search; 9 support/assembly.
    private static final java.util.Map<Integer, Set<String>> FROZEN_DOCUMENT_EDGES = java.util.Map.of(
        8, Set.of(
            edge("service.AdvancedSearchCitationMetadataService", "documents.domain.DocumentUploadNode"),
            edge("service.AdvancedSearchCitationMetadataService", "documents.ports.DocumentUploadRepository"),
            edge("service.DocumentMetadataTextRetriever", "documents.domain.DocumentUploadNode"),
            edge("service.DocumentMetadataTextRetriever", "documents.ports.DocumentUploadRepository")
        ),
        9, Set.of(
            edge("documents.application.inspection.DocumentSourceInputsFacade", "error.NotFoundException"),
            edge("documents.application.inspection.DocumentEvaluationPreparationFacade", "error.NotFoundException"),
            edge("documents.application.processing.DocumentDryExtractionFacade", "service.AiProfileContext"),
            edge("config.PersistenceConfiguration", "documents.adapters.graph.repository.Neo4jDocumentChunkRepository"),
            edge("config.PersistenceConfiguration", "documents.adapters.relational.entity.DocumentUploadEntity"),
            edge("config.PersistenceConfiguration", "documents.adapters.relational.repository.JpaDocumentUploadRepository"),
            edge("documents.adapters.binary.DocumentBinaryStorageAdapter", "storage.BinaryStorageService"),
            edge("documents.adapters.graph.DocumentChunkPersistenceAdapter", "persistence.transaction.GraphTransactional"),
            edge("documents.adapters.graph.DocumentChunkPersistenceAdapter", "repository.LexicalIndexRepository"),
            edge("documents.adapters.graph.DocumentChunkPersistenceAdapter", "service.EmbeddingSpaceIndexService"),
            edge("documents.adapters.graph.GraphArtifactCleanupService", "repository.LexicalIndexRepository"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "logging.LogMetadata"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "observability.AiModelCallObservation"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "observability.AiObservationService"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "observability.AiTokenUsage"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "service.AiProfileContext"),
            edge("documents.adapters.model.SpringAiGraphExtractionClient", "service.AiRuntimeModelFactory"),
            edge("documents.adapters.relational.RelationalDocumentProcessingRunRepository", "persistence.transaction.RelationalTransactional"),
            edge("documents.adapters.relational.RelationalDocumentStorageMutationRepository", "persistence.transaction.RelationalTransactional"),
            edge("documents.api.model.DocumentChunkHierarchyResponse", "dto.PageResponse"),
            edge("documents.api.model.DocumentChunkPageResponse", "dto.PageResponse"),
            edge("documents.application.management.ChunkingStateService", "config.AppProperties"),
            edge("documents.application.management.ChunkingStateService", "config.AppProperties$Model"),
            edge("documents.application.management.ChunkingStateService", "dto.RuntimeSettingResponse"),
            edge("documents.application.management.ChunkingStateService", "persistence.transaction.RelationalTransactional"),
            edge("documents.application.management.ChunkingStateService", "service.RuntimeSettingsService"),
            edge("documents.application.management.ChunkingStateService", "service.RuntimeSettingsService$ChunkingSettings"),
            edge("documents.application.management.DocumentStorageMutationService", "persistence.transaction.RelationalTransactional"),
            edge("documents.application.management.DocumentUploadService", "error.ConflictException"),
            edge("documents.application.management.DocumentUploadService", "error.NotFoundException"),
            edge("documents.application.management.DocumentUploadService", "logging.LogMetadata"),
            edge("documents.application.management.DocumentUploadService", "service.KnowledgeBaseLifecycleService"),
            edge("documents.application.processing.ChunkingService", "domain.AiProfileNode"),
            edge("documents.application.processing.ChunkingService", "service.RuntimeSettingsService"),
            edge("documents.application.processing.ChunkingService", "service.RuntimeSettingsService$ChunkingSettings"),
            edge("documents.application.processing.DocumentMigrationPreparationFacade", "domain.AiProfileNode"),
            edge("documents.application.processing.DocumentMigrationPreparationFacade", "error.EmbeddingSpaceConflictException"),
            edge("documents.application.processing.DocumentMigrationPreparationFacade", "error.NotFoundException"),
            edge("documents.application.processing.DocumentProcessingOptionsRegistry", "error.ProcessingOptionsValidationException"),
            edge("documents.application.processing.DocumentProcessingService", "domain.AiProfileNode"),
            edge("documents.application.processing.DocumentProcessingService", "error.ConflictException"),
            edge("documents.application.processing.DocumentProcessingService", "error.NotFoundException"),
            edge("documents.application.processing.DocumentProcessingService", "logging.LogMetadata"),
            edge("documents.application.processing.DocumentProcessingService", "observability.AiObservationScope"),
            edge("documents.application.processing.DocumentProcessingService", "observability.AiObservationService"),
            edge("documents.application.processing.DocumentProcessingService", "observability.AiWorkflowContext"),
            edge("documents.application.processing.DocumentProcessingService", "service.AiProfileContext"),
            edge("documents.application.processing.DocumentProcessingService", "service.KnowledgeBaseLifecycleService"),
            edge("documents.application.processing.DocumentProcessingService", "service.KnowledgeBaseService"),
            edge("documents.application.processing.DocumentReprocessingFacade", "service.AiProfileContext"),
            edge("documents.application.processing.DocumentReprocessingFacade", "service.KnowledgeBaseService"),
            edge("documents.application.processing.DocumentRunHistoryLifecycle", "persistence.transaction.RelationalTransactional"),
            edge("documents.application.processing.EmbeddingPersistenceStage", "domain.AiProfileNode"),
            edge("documents.application.processing.EmbeddingPersistenceStage", "embedding.EmbeddingClient"),
            edge("documents.application.processing.EmbeddingPersistenceStage", "infrastructure.ai.ProfileScopedAiClientResolver"),
            edge("documents.application.processing.EmbeddingPersistenceStage", "service.AiProfileContext"),
            edge("documents.application.processing.ExtractionRunLifecycle", "persistence.transaction.RelationalTransactional"),
            edge("documents.application.processing.GraphExtractionService", "logging.LogMetadata"),
            edge("documents.application.processing.GraphExtractionService", "observability.AiObservationScope"),
            edge("documents.application.processing.GraphExtractionService", "observability.AiObservationService"),
            edge("documents.application.processing.GraphExtractionService", "observability.AiWorkflowContext"),
            edge("documents.application.processing.GraphExtractionStage", "domain.AiProfileNode"),
            edge("documents.application.processing.GraphExtractionStage", "service.AiProfileContext"),
            edge("documents.application.processing.GraphExtractionValidationService", "error.GraphExtractionValidationException"),
            edge("documents.application.processing.GraphExtractionValidationService", "logging.LogMetadata"),
            edge("documents.application.processing.GraphExtractionValidationService", "service.RuntimeSettingsService"),
            edge("documents.application.processing.GraphExtractionValidationService", "service.RuntimeSettingsService$ExtractionSettings"),
            edge("documents.application.processing.ProcessingRunLifecycle", "persistence.transaction.RelationalTransactional"),
            edge("domain.AiProfileNode", "documents.domain.chunking.TokenizerId"),
            edge("infrastructure.persistence.relational.RelationalAiProfileRepository", "documents.domain.chunking.TokenizerId"),
            edge("service.AiProfileService", "documents.domain.chunking.TokenizerId"),
            edge("service.EmbeddingSpaceIdentity", "documents.domain.chunking.TokenizerId"),
            edge("service.KnowledgeBaseService", "documents.domain.chunking.TokenizerId"),
            edge("service.RuntimeSettingsService", "documents.domain.chunking.ChunkRevisionCalculator"),
            edge("service.RuntimeSettingsService", "documents.domain.chunking.ChunkerRevision"),
            edge("service.RuntimeSettingsService", "documents.domain.chunking.FixedCharacterChunkingStrategy")
        )
    );

    private static String edge(String origin, String target) {
        return BASE_PACKAGE + "." + origin + " -> " + BASE_PACKAGE + "." + target;
    }

    private static Set<String> documentTransitionalEdges(JavaClasses classes) {
        return classes.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> documentTransitionalEdge(d.getOriginClass(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    }

    private static boolean documentTransitionalEdge(JavaClass origin, JavaClass target) {
        boolean ownedOrigin = isInPackage(origin, BASE_PACKAGE + ".documents");
        boolean ownedTarget = isInPackage(target, BASE_PACKAGE + ".documents");
        return !ownedOrigin && !isInPackage(origin, BASE_PACKAGE + ".bootstrap") && ownedTarget
                && !isInPackage(target, BASE_PACKAGE + ".documents.contracts")
            || ownedOrigin && isInPackage(target, BASE_PACKAGE) && !ownedTarget
                && !isInPackage(target, BASE_PACKAGE + ".ai.domain")
                && !target.getName().equals(BASE_PACKAGE + ".ai.application.EmbeddingCompatibility")
                && !isInPackage(target, BASE_PACKAGE + ".schemas.contracts");
    }

    @Test
    void document_transitional_edges_are_exact_and_have_retirement_steps() {
        Set<String> expected = FROZEN_DOCUMENT_EDGES.values().stream().flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> actual = documentTransitionalEdges(PRODUCTION_CLASSES);
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(expected);
        Set<String> stale = new TreeSet<>(expected);
        stale.removeAll(actual);
        assertTrue(added.isEmpty() && stale.isEmpty(),
            "Unlisted document dependencies:\n" + String.join("\n", added)
                + "\nStale document exceptions (remove them):\n" + String.join("\n", stale));
        assertTrue(FROZEN_DOCUMENT_EDGES.keySet().equals(Set.of(8, 9)));
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
                || Set.of(SERVICE_PACKAGE + ".KnowledgeBaseService", SERVICE_PACKAGE + ".KnowledgeBaseLifecycleService", DOMAIN_PACKAGE + ".AiProfileNode")
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
    void remaining_schema_bridge_edges_are_exact() {
        Set<String> actual = PRODUCTION_CLASSES.stream()
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.registry")
                || isInPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.discovery"))
            .filter(d -> !isInPackage(d.getOriginClass(), BASE_PACKAGE + ".schemas")
                && !isInPackage(d.getOriginClass(), BASE_PACKAGE + ".bootstrap"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        Set<String> expected = FROZEN_SCHEMA_BRIDGE_EDGES.values().stream()
            .flatMap(Set::stream).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertTrue(actual.equals(expected), "Schema bridge edges changed; expected " + expected
            + " but found " + actual);
        assertTrue(FROZEN_SCHEMA_BRIDGE_EDGES.keySet().equals(Set.of(8, 9)));
    }

    // Exact assembly dependencies pending roadmap step 9.
    private static final Set<String> FROZEN_DRAFT_LATER_EDGES = Set.of(
        edge("config.PersistenceConfiguration", "schemas.drafts.adapters.relational.entity.SchemaDraftEntity"),
        edge("config.PersistenceConfiguration", "schemas.drafts.adapters.relational.repository.JpaSchemaDraftRepository")
    );

    @Test
    void draft_internal_callers_are_exact_later_slice_dependencies() {
        Set<String> actual = PRODUCTION_CLASSES.stream()
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInAnyPackage(d.getTargetClass(), BASE_PACKAGE + ".schemas.drafts.application",
                BASE_PACKAGE + ".schemas.drafts.domain", BASE_PACKAGE + ".schemas.drafts.ports",
                BASE_PACKAGE + ".schemas.drafts.adapters"))
            .filter(d -> !isInPackage(d.getOriginClass(), BASE_PACKAGE + ".schemas.drafts")
                && !isInPackage(d.getOriginClass(), BASE_PACKAGE + ".bootstrap.integration.schemas"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertTrue(actual.equals(FROZEN_DRAFT_LATER_EDGES), "Draft later edges changed: " + actual);
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
            || Set.of(SERVICE_PACKAGE + ".KnowledgeBaseService", SERVICE_PACKAGE + ".KnowledgeBaseLifecycleService",
                DOMAIN_PACKAGE + ".AiProfileNode").contains(target.getName());
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
        io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository knowledgeBases;
        io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository documents;
        io.github.vfedoriv.graphrag.domain.AiProfileNode mutableProfile;
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
                BASE_PACKAGE + ".documents.application",
                BASE_PACKAGE + ".documents.adapters",
                BASE_PACKAGE + ".documents.ports",
                BASE_PACKAGE + ".documents.domain",
                BASE_PACKAGE + ".bootstrap")
            || target.getName().equals(REPOSITORY_PACKAGE + ".KnowledgeBaseRepository")
            || target.getName().equals(DOMAIN_PACKAGE + ".KnowledgeBaseNode")
            || target.getSimpleName().equals("JpaKnowledgeBaseSchemaRepository");
    }

    private static class ForbiddenSchemaBoundaryFixture {
        io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository knowledgeBases;
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
    void compatibility_bridge_exception_sets_reject_stale_callers() {
        assertExactCallers(SERVICE_PACKAGE + ".EmbeddingSpacePolicy", FROZEN_EMBEDDING_POLICY_CALLERS);
        assertExactCallers(SERVICE_PACKAGE + ".EmbeddingSpaceIdentity", FROZEN_IDENTITY_CALLERS);
        assertExactCallers(SERVICE_PACKAGE + ".EmbeddingSpace", FROZEN_SPACE_CALLERS);
    }

    private static void assertExactCallers(String target, Set<String> expected) {
        Set<String> actual = PRODUCTION_CLASSES.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> d.getTargetClass().getName().equals(target))
            .map(d -> d.getOriginClass().getName()).collect(java.util.stream.Collectors.toSet());
        Set<String> externalExpected = expected.stream().filter(name -> !name.equals(target))
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(actual.equals(externalExpected), "Expected exact callers of " + target + ": "
            + externalExpected + "; observed: " + actual);
    }

    @Test
    void document_guards_reject_new_foreign_callers_and_pure_rule_effects() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ForbiddenDocumentFixture.class);
        Set<String> edges = documentTransitionalEdges(fixture);
        Set<String> frozen = FROZEN_DOCUMENT_EDGES.values().stream().flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(edges.stream().anyMatch(e -> e.endsWith("documents.ports.DocumentUploadRepository") && !frozen.contains(e)));
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
    void direct_neo4j_client_usage_stays_in_persistence_adapters_or_frozen_legacy_exceptions() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
                .filter(javaClass -> !isInAnyPackage(javaClass, INFRASTRUCTURE_PERSISTENCE_PACKAGE, BASE_PACKAGE + ".documents.adapters"))
                .filter(javaClass -> !FROZEN_LEGACY_NEO4J_CLIENT_EXCEPTIONS.contains(javaClass.getName()))
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
                .filter(javaClass -> !isInAnyPackage(javaClass, RELATIONAL_REPOSITORY_PACKAGE, BASE_PACKAGE + ".documents.adapters.relational.repository",
                    BASE_PACKAGE + ".knowledgebase.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.registry.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.drafts.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.evaluation.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.publication.adapters.relational.repository",
                    BASE_PACKAGE + ".schemas.reprocessing.adapters.relational.repository"))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Neo4jRepository.class))
                .filter(javaClass -> !isInAnyPackage(javaClass, REPOSITORY_PACKAGE, BASE_PACKAGE + ".documents.adapters.graph.repository"))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .filter(javaClass -> !isInAnyPackage(javaClass, RELATIONAL_ENTITY_PACKAGE, BASE_PACKAGE + ".documents.adapters.relational.entity",
                    BASE_PACKAGE + ".knowledgebase.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.registry.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.drafts.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.evaluation.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.publication.adapters.relational.entity",
                    BASE_PACKAGE + ".schemas.reprocessing.adapters.relational.entity"))
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
        for (JavaClass javaClass : PRODUCTION_CLASSES) {
            if (FROZEN_LEGACY_TRANSACTIONAL_SELF_INVOCATION_EXCEPTIONS.contains(javaClass.getName())) {
                continue;
            }
            for (JavaMethodCall methodCall : javaClass.getMethodCallsFromSelf()) {
                if (!methodCall.getOriginOwner().equals(methodCall.getTargetOwner())) {
                    continue;
                }
                Optional<JavaMethod> targetMethod = methodCall.getTarget().resolveMember();
                if (targetMethod.isPresent() && isStoreTransactional(targetMethod.get())) {
                    violations.add(methodCall.getDescription());
                }
            }
        }

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
            .filter(c -> Set.of(SERVICE_PACKAGE + ".KnowledgeBaseService", SERVICE_PACKAGE + ".AiProfileService",
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
        boolean tokenizerValueBridge = Set.of(SERVICE_PACKAGE + ".AiProfileService", SERVICE_PACKAGE + ".KnowledgeBaseService")
            .contains(origin) && target.getName().equals(BASE_PACKAGE + ".documents.domain.chunking.TokenizerId");
        return isDocumentImplementation(target) && !tokenizerValueBridge
            || target.getName().equals(SERVICE_PACKAGE + ".GraphArtifactCleanupService")
            || (origin.equals(SERVICE_PACKAGE + ".AiProfileService") || origin.startsWith(BASE_PACKAGE + ".ai."))
                && (isInPackage(target, BASE_PACKAGE + ".knowledgebase")
                    || target.getSimpleName().contains("KnowledgeBase"));
    }

    private static final Set<String> FROZEN_EMBEDDING_POLICY_CALLERS = Set.of(
        SERVICE_PACKAGE + ".AdvancedSearchReadinessService",
        SERVICE_PACKAGE + ".DenseTextRetriever");

    @Test
    void ai_rules_ports_and_provider_contracts_are_pure_immutable_values() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> isInAnyPackage(c, BASE_PACKAGE + ".ai.domain", BASE_PACKAGE + ".ai.ports",
                BASE_PACKAGE + ".knowledgebase.ports", BASE_PACKAGE + ".knowledgebase.contracts"))
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
                BASE_PACKAGE + ".knowledgebase.application"))
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
            || isDraftFactPort(target)
            || isInAnyPackage(target, BASE_PACKAGE + ".ai.ports",
                BASE_PACKAGE + ".documents.contracts", BASE_PACKAGE + ".schemas.registry.ports",
                BASE_PACKAGE + ".schemas.discovery.ports", BASE_PACKAGE + ".schemas.evaluation.ports",
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
    void features_never_depend_on_bootstrap_and_bridge_callers_are_frozen() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> !isInPackage(c, BASE_PACKAGE + ".bootstrap"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> isInPackage(d.getTargetClass(), BASE_PACKAGE + ".bootstrap")
                || !bridgeCallerAllowed(d.getOriginClass(), d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    private static boolean bridgeCallerAllowed(JavaClass origin, JavaClass target) {
        return !target.getName().equals(SERVICE_PACKAGE + ".EmbeddingSpacePolicy")
            || FROZEN_EMBEDDING_POLICY_CALLERS.contains(origin.getName());
    }

    private static final Set<String> FROZEN_IDENTITY_CALLERS = Set.of(
        SERVICE_PACKAGE + ".EmbeddingSpaceIdentity", SERVICE_PACKAGE + ".EmbeddingSpacePolicy",
        SERVICE_PACKAGE + ".EmbeddingSpaceIndexService",
        SERVICE_PACKAGE + ".LexicalIndexIdentity");
    private static final Set<String> FROZEN_SPACE_CALLERS = Set.of(
        SERVICE_PACKAGE + ".EmbeddingSpace", SERVICE_PACKAGE + ".EmbeddingSpaceIdentity",
        SERVICE_PACKAGE + ".EmbeddingSpacePolicy", SERVICE_PACKAGE + ".EmbeddingSpaceIndexService",
        SERVICE_PACKAGE + ".DenseTextRetriever");

    @Test
    void legacy_identity_and_value_bridges_cannot_gain_callers_or_state_access() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> d.getTargetClass().getName().equals(SERVICE_PACKAGE + ".EmbeddingSpaceIdentity")
                    && !FROZEN_IDENTITY_CALLERS.contains(d.getOriginClass().getName())
                || d.getTargetClass().getName().equals(SERVICE_PACKAGE + ".EmbeddingSpace")
                    && !FROZEN_SPACE_CALLERS.contains(d.getOriginClass().getName())
                || d.getOriginClass().getName().equals(SERVICE_PACKAGE + ".EmbeddingSpaceIdentity")
                    && !identityDependencyAllowed(d.getTargetClass())
                || d.getOriginClass().getName().equals(SERVICE_PACKAGE + ".EmbeddingSpace")
                    && !isInAnyPackage(d.getTargetClass(), "java.lang", "java.lang.invoke", "java.lang.runtime"))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    private static boolean identityDependencyAllowed(JavaClass target) {
        return isInAnyPackage(target, "java.lang", "java.util", "java.security", "java.nio.charset")
            || Set.of(SERVICE_PACKAGE + ".EmbeddingSpace", DOMAIN_PACKAGE + ".AiProfileNode",
                BASE_PACKAGE + ".ai.domain.EmbeddingTarget", BASE_PACKAGE + ".documents.domain.chunking.TokenizerId").contains(target.getName());
    }

    @Test
    void compatibility_bridge_dependencies_cannot_expand() {
        Set<String> violations = PRODUCTION_CLASSES.stream()
            .filter(c -> c.getName().equals(SERVICE_PACKAGE + ".EmbeddingSpacePolicy"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> !bridgeDependencyAllowed(d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    private static boolean bridgeDependencyAllowed(JavaClass target) {
        return isInAnyPackage(target, "java.lang", "java.util", "org.springframework.stereotype") || Set.of(
            BASE_PACKAGE + ".ai.application.EmbeddingCompatibility", BASE_PACKAGE + ".ai.domain.EmbeddingTarget",
            DOMAIN_PACKAGE + ".AiProfileNode", SERVICE_PACKAGE + ".EmbeddingSpace",
            SERVICE_PACKAGE + ".EmbeddingSpaceIdentity").contains(target.getName());
    }

    @Test
    void negative_fixtures_reject_foreign_state_provider_bypasses_and_new_bridge_callers() {
        JavaClass forbidden = new ClassFileImporter().importClasses(ForbiddenAiStateFixture.class)
            .get(ForbiddenAiStateFixture.class);
        Set<String> foreign = forbidden.getDirectDependenciesFromSelf().stream()
            .filter(d -> migratedDependencyForbidden(SERVICE_PACKAGE + ".AiProfileService", d.getTargetClass()))
            .map(ArchitectureBoundaryTest::format).collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertTrue(foreign.stream().anyMatch(v -> v.contains("DocumentChunkRepository")));
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d -> !bridgeDependencyAllowed(d.getTargetClass())));
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
        assertTrue(forbidden.getDirectDependenciesFromSelf().stream().anyMatch(d -> !bridgeCallerAllowed(forbidden, d.getTargetClass())));
    }

    @Test
    void migrated_guards_reject_ai_assignment_bypasses_and_relational_adapter_clients() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenAiStateFixture.class).get(ForbiddenAiStateFixture.class);
        Set<String> forbidden = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> migratedDependencyForbidden(SERVICE_PACKAGE + ".AiProfileService", d.getTargetClass()))
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
        io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository assignments;
        io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode knowledgeBase;
        io.github.vfedoriv.graphrag.domain.AiProfileNode profile;
        io.github.vfedoriv.graphrag.documents.contracts.StoredEmbeddings provider;
        io.github.vfedoriv.graphrag.bootstrap.integration.ai.StoredEmbeddingInformationAdapter adapter;
        io.github.vfedoriv.graphrag.service.EmbeddingSpacePolicy newBridgeCaller;
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
}
