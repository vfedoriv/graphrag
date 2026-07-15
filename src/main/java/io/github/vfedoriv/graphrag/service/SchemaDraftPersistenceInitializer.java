package io.github.vfedoriv.graphrag.infrastructure.persistence;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SchemaDraftPersistenceInitializer implements ApplicationRunner {
    private final Neo4jClient neo4jClient;

    public SchemaDraftPersistenceInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> statements = List.of(
            "CREATE CONSTRAINT schema_draft_id IF NOT EXISTS FOR (n:SchemaDraft) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_definition_identity IF NOT EXISTS FOR (n:SchemaDefinition) REQUIRE (n.name, n.version) IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_source_id IF NOT EXISTS FOR (n:SchemaDraftSource) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_run_id IF NOT EXISTS FOR (n:SchemaDraftAnalysisRun) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_analysis_lease_id IF NOT EXISTS FOR (n:SchemaDraftAnalysisLease) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_evaluation_run_id IF NOT EXISTS FOR (n:SchemaDraftEvaluationRun) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_evaluation_outcome_id IF NOT EXISTS FOR (n:SchemaDraftEvaluationOutcome) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_publication_id IF NOT EXISTS FOR (n:SchemaDraftPublication) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_publication_draft IF NOT EXISTS FOR (n:SchemaDraftPublication) REQUIRE n.draftId IS UNIQUE",
            "CREATE CONSTRAINT schema_draft_publication_target IF NOT EXISTS FOR (n:SchemaDraftPublication) REQUIRE n.targetIdentity IS UNIQUE",
            "CREATE CONSTRAINT schema_reprocessing_plan_id IF NOT EXISTS FOR (n:SchemaReprocessingPlan) REQUIRE n.id IS UNIQUE",
            "CREATE CONSTRAINT schema_reprocessing_item_id IF NOT EXISTS FOR (n:SchemaReprocessingItem) REQUIRE n.id IS UNIQUE",
            "CREATE INDEX schema_draft_owner IF NOT EXISTS FOR (n:SchemaDraft) ON (n.knowledgeBaseId)",
            "CREATE INDEX schema_draft_source_active IF NOT EXISTS FOR (n:SchemaDraftSource) ON (n.draftId, n.status)",
            "CREATE INDEX schema_draft_run_status IF NOT EXISTS FOR (n:SchemaDraftAnalysisRun) ON (n.draftId, n.status)",
            "CREATE INDEX schema_draft_result_reuse IF NOT EXISTS FOR (n:SchemaDraftSourceResult) ON (n.draftId, n.reuseKey, n.status)",
            "CREATE INDEX schema_draft_evaluation_status IF NOT EXISTS FOR (n:SchemaDraftEvaluationRun) ON (n.draftId, n.status)",
            "CREATE INDEX schema_draft_evaluation_reuse IF NOT EXISTS FOR (n:SchemaDraftEvaluationOutcome) ON (n.draftId, n.reuseKey, n.status)",
            "CREATE INDEX schema_reprocessing_plan_status IF NOT EXISTS FOR (n:SchemaReprocessingPlan) ON (n.knowledgeBaseId, n.status)",
            "CREATE INDEX schema_reprocessing_item_status IF NOT EXISTS FOR (n:SchemaReprocessingItem) ON (n.planId, n.status)"
        );
        statements.forEach(statement -> neo4jClient.query(statement).run());
    }
}
