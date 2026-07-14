package io.github.vfedoriv.graphrag.infrastructure.persistence;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaDraftGraphService {
    private final Neo4jClient neo4jClient;

    public SchemaDraftGraphService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Transactional
    public void attach(String draftId, String childLabel, String childId) {
        if (!allowedLabel(childLabel)) {
            throw new IllegalArgumentException("Unsupported draft child label");
        }
        neo4jClient.query("MATCH (d:SchemaDraft {id: $draftId}), (c:" + childLabel
                + " {id: $childId}) MERGE (c)-[:BELONGS_TO_DRAFT]->(d)")
            .bind(draftId).to("draftId")
            .bind(childId).to("childId")
            .run();
    }

    private boolean allowedLabel(String label) {
        return switch (label) {
            case "SchemaDraftSource", "SchemaDraftSourceRevision", "SchemaDraftAnalysisRun", "SchemaDraftSourceResult",
                "SchemaDraftAggregateRevision", "SchemaDraftDecision", "SchemaDraftConflict",
                "SchemaDraftStorageMutation" -> true;
            default -> false;
        };
    }
}
