package io.github.vfedoriv.graphrag;

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
            BASE_PACKAGE + ".domain.DocumentChunkNode"
    );
    private static final Set<String> ALLOWED_NEO4J_REPOSITORIES = Set.of(
            BASE_PACKAGE + ".repository.DocumentChunkRepository"
    );

    private static final Set<String> FROZEN_LEGACY_NEO4J_CLIENT_EXCEPTIONS = Set.of(
            BASE_PACKAGE + ".graph.GraphWriteService",
            BASE_PACKAGE + ".config.PersistenceConfiguration",
            BASE_PACKAGE + ".service.EmbeddingSpaceIndexService",
            BASE_PACKAGE + ".service.GraphArtifactCleanupService"
    );
    private static final Set<String> FROZEN_LEGACY_TRANSACTIONAL_SELF_INVOCATION_EXCEPTIONS = Set.of(
            BASE_PACKAGE + ".service.AiProfileService",
            BASE_PACKAGE + ".service.KnowledgeBaseService",
            BASE_PACKAGE + ".service.RuntimeSettingsService",
            BASE_PACKAGE + ".service.SchemaDraftEvaluationEligibilityService",
            BASE_PACKAGE + ".service.SchemaDraftLifecycleService",
            BASE_PACKAGE + ".service.SchemaDraftPublicationService",
            BASE_PACKAGE + ".service.SchemaDraftReviewService",
            BASE_PACKAGE + ".service.SchemaDraftSourceService",
            BASE_PACKAGE + ".service.SchemaRegistryService"
    );

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE);

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
                .filter(javaClass -> !isInPackage(javaClass, INFRASTRUCTURE_PERSISTENCE_PACKAGE))
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
                .filter(javaClass -> !isInPackage(javaClass, RELATIONAL_REPOSITORY_PACKAGE))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Neo4jRepository.class))
                .filter(javaClass -> !isInPackage(javaClass, REPOSITORY_PACKAGE))
                .map(JavaClass::getName)
                .forEach(violations::add);
        PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .filter(javaClass -> !isInPackage(javaClass, RELATIONAL_ENTITY_PACKAGE))
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
            .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
            .filter(dependency -> !dependency.getTargetClass().getName().startsWith("java.")
                && !dependency.getTargetClass().getPackageName().equals(dependency.getOriginClass().getPackageName()))
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
                        BASE_PACKAGE + ".bootstrap.integration.reprocessing"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        assertNoViolations(violations);
    }

    @Test
    void execution_collaborator_uses_only_its_port_and_has_no_transaction() {
        Set<String> violations = dependenciesFromClassesIn(BASE_PACKAGE + ".schemas.reprocessing.application").stream()
            .filter(dependency -> isInfrastructureClient(dependency.getTargetClass())
                || isInPackage(dependency.getTargetClass(), BASE_PACKAGE)
                    && !isInAnyPackage(dependency.getTargetClass(),
                        BASE_PACKAGE + ".schemas.reprocessing.ports", BASE_PACKAGE + ".schemas.reprocessing.application"))
            .map(ArchitectureBoundaryTest::format)
            .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        PRODUCTION_CLASSES.stream()
            .filter(javaClass -> isInAnyPackage(javaClass, BASE_PACKAGE + ".schemas.reprocessing.application",
                BASE_PACKAGE + ".bootstrap.integration.reprocessing", BASE_PACKAGE + ".documents.application.processing"))
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
            io.github.vfedoriv.graphrag.service.SchemaReprocessingPlanService.class);
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
        private io.github.vfedoriv.graphrag.repository.DocumentUploadRepository documents;
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
        private io.github.vfedoriv.graphrag.repository.DocumentUploadRepository documents;

        void processItem() {
            documents.findById("document");
        }
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
            || target.isAssignableTo(Files.class);
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
