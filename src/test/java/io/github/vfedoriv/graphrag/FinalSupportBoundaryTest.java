package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService;
import io.github.vfedoriv.graphrag.settings.domain.RuntimeSettingOverrideNode;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FinalSupportBoundaryTest {
    private static final String BASE = "io.github.vfedoriv.graphrag.";
    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests()).importPackages(BASE.substring(0, BASE.length() - 1));

    @Test
    void settings_does_not_interpret_feature_policy_or_chunking() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> c.getSimpleName().startsWith("RuntimeSetting"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> in(d.getTargetClass(), "documents") || in(d.getTargetClass(), "search"))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName())
            .forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void foreign_consumers_cannot_import_settings_management_or_state() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> !in(c, "settings") && !in(c, "bootstrap"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> d.getTargetClass().getSimpleName().equals("RuntimeSettingsService")
                || d.getTargetClass().getSimpleName().equals("RuntimeSettingOverrideNode"))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName())
            .forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void generic_observation_support_has_no_search_interpretation() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> in(c, "observability"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream()).filter(d -> in(d.getTargetClass(), "search"))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void legacy_business_and_assembly_packages_have_no_remaining_implementations() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> in(c, "service") || in(c, "controller") || in(c, "dto")
            || in(c, "domain") || in(c, "repository") || in(c, "config") || in(c, "application.settings")
            || in(c, "infrastructure"))
            .filter(c -> !c.getSimpleName().equals("package-info"))
            .map(JavaClass::getName).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void features_use_public_foreign_capabilities_and_values() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> owner(c) != null)
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> foreignImplementation(d.getOriginClass(), d.getTargetClass()))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void only_index_owners_and_concrete_assembly_can_import_index_adapters() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> in(d.getTargetClass(), "indexes.adapters")
                && foreignImplementation(d.getOriginClass(), d.getTargetClass()))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void features_and_support_never_import_bootstrap_implementations() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> !in(c, "bootstrap"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream()).filter(d -> in(d.getTargetClass(), "bootstrap"))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void mapping_adapters_have_only_public_values_and_consumer_ports() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> in(c, "bootstrap.integration"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> !mappingAllowed(d.getTargetClass()))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        CLASSES.stream().filter(c -> in(c, "bootstrap.integration"))
            .flatMap(c -> c.getMethods().stream())
            .filter(m -> m.getAnnotations().stream().anyMatch(a -> a.getRawType().getSimpleName().endsWith("Transactional")))
            .map(m -> m.getFullName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void deterministic_values_cannot_resolve_live_settings_or_import_effects() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> c.getPackageName().contains(".domain"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> d.getTargetClass().getName().equals(BASE + "settings.contracts.RuntimeSettingsAccess")
                || effect(d.getTargetClass())
                || d.getTargetClass().getName().startsWith(BASE)
                    && (d.getTargetClass().getPackageName().contains(".application")
                        || d.getTargetClass().getPackageName().contains(".adapters")
                        || d.getTargetClass().getPackageName().contains(".ports")
                        || d.getTargetClass().getPackageName().contains(".api")))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void provider_handles_are_confined_to_model_adapters_and_ai_resolution() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> providerHandle(d.getTargetClass()) && !modelRole(d.getOriginClass())
                && !(in(d.getOriginClass(), "bootstrap") && !in(d.getOriginClass(), "bootstrap.integration")))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void support_cannot_depend_on_feature_interpretation_or_state() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> in(c, "observability") || in(c, "logging") || in(c, "storage") || in(c, "indexes")
            || in(c, "http.contracts") || in(c, "persistence.transaction"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> owner(d.getTargetClass()) != null && !publicValueOrCapability(d.getTargetClass()))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void boundary_negative_fixtures_reject_neighbor_implementations_and_reverse_dependencies() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenBoundaryFixture.class).get(ForbiddenBoundaryFixture.class);
        JavaClass workflow = CLASSES.get(BASE + "documents.application.processing.DocumentProcessingService");
        Set<String> rejected = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> foreignImplementation(workflow, d.getTargetClass()))
            .map(d -> d.getTargetClass().getSimpleName()).collect(java.util.stream.Collectors.toSet());
        assertTrue(rejected.containsAll(Set.of("RuntimeSettingsService", "RuntimeSettingOverrideNode",
            "AiProfileNode", "KnowledgeBaseRepository", "AiRuntimeModelFactory", "ModelProperties")), rejected.toString());
        JavaClass settings = CLASSES.get(BASE + "settings.application.RuntimeSettingsService");
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            d.getTargetClass().getSimpleName().equals("DocumentChunkRevisions") && foreignImplementation(settings, d.getTargetClass())));
        JavaClass ai = CLASSES.get(BASE + "ai.profiles.application.AiProfileService");
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            d.getTargetClass().getSimpleName().equals("KnowledgeBaseProfiles") && foreignImplementation(ai, d.getTargetClass())));
        Set<String> mappingRejected = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> !mappingAllowed(d.getTargetClass())).map(d -> d.getTargetClass().getSimpleName())
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(mappingRejected.containsAll(Set.of("ChatModel", "Neo4jClient", "TransactionTemplate",
            "Path", "RuntimeSettingsService", "KnowledgeBaseRepository")), mappingRejected.toString());
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            d.getTargetClass().getSimpleName().equals("ProfileFacts") && !foreignImplementation(workflow, d.getTargetClass())));
        assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
            d.getTargetClass().getSimpleName().equals("ChunkingSettings") && publicValueOrCapability(d.getTargetClass())));
    }

    @Test
    void public_fact_values_are_immutable_and_exclude_clients_state_and_credentials() {
        Set<String> violations = new TreeSet<>();
        CLASSES.stream().filter(c -> in(c, "ai.contracts") || in(c, "settings.contracts") || in(c, "knowledgebase.contracts"))
            .flatMap(c -> c.getFields().stream())
            .filter(f -> !f.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                || f.getName().equals("apiKey"))
            .map(f -> f.getFullName()).forEach(violations::add);
        CLASSES.stream().filter(c -> in(c, "ai.contracts") || in(c, "settings.contracts") || in(c, "knowledgebase.contracts"))
            .flatMap(c -> c.getDirectDependenciesFromSelf().stream())
            .filter(d -> effect(d.getTargetClass()) || providerHandle(d.getTargetClass())
                || owner(d.getTargetClass()) != null && !publicValueOrCapability(d.getTargetClass()))
            .map(d -> d.getOriginClass().getName() + " -> " + d.getTargetClass().getName()).forEach(violations::add);
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void domain_negative_fixture_rejects_both_store_transaction_annotations() {
        JavaClass fixture = new ClassFileImporter().importClasses(TransactionalDomainFixture.class)
            .get(TransactionalDomainFixture.class);
        Set<String> rejected = fixture.getDirectDependenciesFromSelf().stream()
            .filter(d -> effect(d.getTargetClass())).map(d -> d.getTargetClass().getSimpleName())
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(rejected.containsAll(Set.of("RelationalTransactional", "GraphTransactional")), rejected.toString());
    }

    @Test
    void every_feature_rejects_index_adapter_neighbors_and_allows_contracts() {
        JavaClass fixture = new ClassFileImporter().importClasses(ForbiddenBoundaryFixture.class)
            .get(ForbiddenBoundaryFixture.class);
        for (String workflow : new String[] {
            "documents.application.processing.DocumentProcessingService",
            "search.runs.application.AdvancedSearchRunService",
            "schemas.registry.application.SchemaRegistryService",
            "knowledgebase.application.KnowledgeBaseService",
            "ai.profiles.application.AiProfileService",
            "settings.application.RuntimeSettingsService",
            "observability.AiObservationService",
            "bootstrap.integration.search.SearchSchemaAdapter"
        }) {
            JavaClass origin = CLASSES.get(BASE + workflow);
            assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
                d.getTargetClass().getSimpleName().equals("EmbeddingSpaceIndexService")
                    && foreignImplementation(origin, d.getTargetClass())), workflow);
            assertTrue(fixture.getDirectDependenciesFromSelf().stream().anyMatch(d ->
                d.getTargetClass().getSimpleName().equals("VectorIndexes")
                    && !foreignImplementation(origin, d.getTargetClass())), workflow);
        }
    }

    private static class TransactionalDomainFixture {
        @io.github.vfedoriv.graphrag.persistence.transaction.RelationalTransactional
        void relational() {}
        @io.github.vfedoriv.graphrag.persistence.transaction.GraphTransactional
        void graph() {}
    }

    private static class ForbiddenBoundaryFixture {
        io.github.vfedoriv.graphrag.settings.application.RuntimeSettingsService management;
        io.github.vfedoriv.graphrag.settings.domain.RuntimeSettingOverrideNode state;
        io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode profileState;
        io.github.vfedoriv.graphrag.ai.configuration.ModelProperties credentials;
        io.github.vfedoriv.graphrag.ai.adapters.provider.AiRuntimeModelFactory provider;
        io.github.vfedoriv.graphrag.knowledgebase.ports.KnowledgeBaseRepository persistence;
        io.github.vfedoriv.graphrag.knowledgebase.contracts.KnowledgeBaseProfiles reverseCapability;
        io.github.vfedoriv.graphrag.documents.contracts.DocumentChunkRevisions reverseDocumentCapability;
        io.github.vfedoriv.graphrag.ai.contracts.ProfileFacts allowedFacts;
        io.github.vfedoriv.graphrag.settings.contracts.RuntimeSettingsAccess.ChunkingSettings allowedSnapshot;
        org.springframework.ai.chat.model.ChatModel client;
        org.springframework.data.neo4j.core.Neo4jClient database;
        org.springframework.transaction.support.TransactionTemplate transaction;
        java.nio.file.Path filesystem;
        io.github.vfedoriv.graphrag.indexes.adapters.graph.EmbeddingSpaceIndexService indexAdapter;
        io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes indexContract;
    }

    static boolean foreignImplementation(JavaClass origin, JavaClass target) {
        String from = owner(origin);
        String to = owner(target);
        if (in(target, "indexes.adapters")) return !in(origin, "indexes")
            && !(in(origin, "bootstrap") && !in(origin, "bootstrap.integration"));
        if (in(target, "indexes.configuration")) return !in(origin, "indexes")
            && !origin.getPackageName().contains(".adapters.graph") && !in(origin, "bootstrap");
        if (from == null || to == null || from.equals(to)) return false;
        if (in(target, "ai.models")) return !modelRole(origin)
            && !Set.of("AiModelPreparation", "EmbeddingClientAccess", "EmbeddingClient").contains(target.getSimpleName());
        if (!allowedDirection(from, to)) return true;
        return !publicValueOrCapability(target);
    }

    static boolean publicValueOrCapability(JavaClass type) {
        return in(type, "documents.contracts") || in(type, "schemas.contracts")
            || type.getPackageName().startsWith(BASE + "schemas.") && type.getPackageName().contains(".contracts")
            || in(type, "knowledgebase.contracts") || in(type, "ai.contracts") || in(type, "ai.domain")
            || in(type, "ai.execution") || in(type, "ai.api.error") || in(type, "settings.contracts")
            || type.getName().equals(BASE + "ai.application.EmbeddingCompatibility");
    }

    static boolean mappingAllowed(JavaClass type) {
        if (effect(type) || providerHandle(type) || type.getSimpleName().endsWith("Repository")
            || type.getName().contains("Transactional")) return false;
        return !type.getName().startsWith(BASE) || in(type, "bootstrap.integration")
            || publicValueOrCapability(type) || in(type, "http.contracts") || type.getPackageName().contains(".ports")
            || type.getName().startsWith(BASE + "schemas.evaluation.domain.EvaluationObservations$");
    }

    private static boolean allowedDirection(String from, String to) {
        if (to.equals("settings")) return true;
        return switch (from) {
            case "search" -> Set.of("documents", "schemas", "knowledgebase", "ai").contains(to);
            case "documents" -> Set.of("schemas", "knowledgebase", "ai").contains(to);
            case "schemas" -> Set.of("knowledgebase", "ai").contains(to);
            case "knowledgebase", "settings" -> to.equals("ai");
            default -> false;
        };
    }

    private static String owner(JavaClass type) {
        for (String area : new String[] {"documents", "schemas", "search", "knowledgebase", "ai", "settings"}) {
            if (in(type, area)) return area;
        }
        return null;
    }

    private static boolean modelRole(JavaClass type) {
        return type.getPackageName().contains(".adapters.model") || in(type, "ai.adapters.provider") || in(type, "ai.models");
    }

    private static boolean providerHandle(JavaClass type) {
        return type.getName().equals("org.springframework.ai.chat.model.ChatModel")
            || type.getName().equals("org.springframework.ai.embedding.EmbeddingModel")
            || type.getName().startsWith("org.springframework.ai.openai.")
            || type.getName().startsWith("dev.langchain4j.model.") && !type.getSimpleName().equals("TokenCountEstimator");
    }

    private static boolean effect(JavaClass type) {
        String name = type.getName();
        return in(type, "persistence.transaction") || name.startsWith("org.neo4j.driver.") || name.equals("org.springframework.data.neo4j.core.Neo4jClient")
            || name.startsWith("org.springframework.jdbc.") || name.startsWith("org.springframework.transaction.") || name.startsWith("jakarta.persistence.")
            || name.startsWith("java.sql.") || name.equals("javax.sql.DataSource")
            || name.startsWith("java.nio.file.") || name.startsWith("java.net.http.");
    }

    private static boolean in(JavaClass type, String area) {
        return type.getPackageName().equals(BASE + area) || type.getPackageName().startsWith(BASE + area + ".");
    }
}
