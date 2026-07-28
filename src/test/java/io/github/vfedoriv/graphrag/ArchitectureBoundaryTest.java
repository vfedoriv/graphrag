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

    private static final Set<String> FROZEN_LEGACY_NEO4J_CLIENT_EXCEPTIONS = Set.of(
            BASE_PACKAGE + ".graph.GraphWriteService",
            BASE_PACKAGE + ".config.PersistenceConfiguration",
            BASE_PACKAGE + ".service.EmbeddingSpaceIndexService",
            BASE_PACKAGE + ".service.EmbeddingSpaceMigrationService",
            BASE_PACKAGE + ".service.GraphArtifactCleanupService",
            BASE_PACKAGE + ".service.GraphExtractionService",
            BASE_PACKAGE + ".service.GraphProvenanceMigrationService",
            BASE_PACKAGE + ".service.HybridSearchService",
            BASE_PACKAGE + ".service.KnowledgeBaseService",
            BASE_PACKAGE + ".service.KnowledgeBaseLifecycleMigrationService",
            BASE_PACKAGE + ".service.Neo4jPersistenceVersionBackfillService",
            BASE_PACKAGE + ".service.SchemaRegistryService"
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

    private final JavaClasses productionClasses = new ClassFileImporter()
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
        Set<String> violations = productionClasses.stream()
                .filter(javaClass -> !isInPackage(javaClass, CONTROLLER_PACKAGE))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> isInPackage(dependency.getTargetClass(), CONTROLLER_PACKAGE))
                .map(ArchitectureBoundaryTest::format)
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);

        assertNoViolations(violations);
    }

    @Test
    void direct_neo4j_client_usage_stays_in_persistence_adapters_or_frozen_legacy_exceptions() {
        Set<String> violations = productionClasses.stream()
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
        Set<String> violations = productionClasses.stream()
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
        productionClasses.stream()
                .filter(javaClass -> javaClass.isAssignableTo(JpaRepository.class))
                .filter(javaClass -> !isInPackage(javaClass, RELATIONAL_REPOSITORY_PACKAGE))
                .map(JavaClass::getName)
                .forEach(violations::add);
        productionClasses.stream()
                .filter(javaClass -> javaClass.isAssignableTo(Neo4jRepository.class))
                .filter(javaClass -> !isInPackage(javaClass, REPOSITORY_PACKAGE))
                .map(JavaClass::getName)
                .forEach(violations::add);
        productionClasses.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
                .filter(javaClass -> !isInPackage(javaClass, RELATIONAL_ENTITY_PACKAGE))
                .map(JavaClass::getName)
                .forEach(violations::add);
        productionClasses.stream()
                .filter(javaClass -> isInPackage(javaClass, RELATIONAL_ENTITY_PACKAGE))
                .filter(javaClass -> javaClass.isAnnotatedWith(Node.class))
                .map(JavaClass::getName)
                .forEach(violations::add);

        assertNoViolations(violations);
    }

    @Test
    void transactional_methods_are_not_called_through_self_invocation() {
        Set<String> violations = new TreeSet<>();
        for (JavaClass javaClass : productionClasses) {
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

    private static boolean isStoreTransactional(JavaMethod method) {
        return method.isAnnotatedWith(GraphTransactional.class)
                || method.isAnnotatedWith(RelationalTransactional.class)
                || method.getOwner().isAnnotatedWith(GraphTransactional.class)
                || method.getOwner().isAnnotatedWith(RelationalTransactional.class);
    }

    private Set<Dependency> dependenciesFromClassesIn(String packageName) {
        Set<Dependency> dependencies = new TreeSet<>(Comparator.comparing(ArchitectureBoundaryTest::format));
        productionClasses.stream()
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
