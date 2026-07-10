package io.github.vfedoriv.graphrag.service;

import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
public class KnowledgeBaseLifecycleMigrationService implements ApplicationRunner {
    private final AiProfileService aiProfileService;
    private final KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;
    private final Neo4jClient neo4jClient;

    public KnowledgeBaseLifecycleMigrationService(
        AiProfileService aiProfileService,
        KnowledgeBaseLifecycleService knowledgeBaseLifecycleService,
        Neo4jClient neo4jClient
    ) {
        this.aiProfileService = aiProfileService;
        this.knowledgeBaseLifecycleService = knowledgeBaseLifecycleService;
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        aiProfileService.seedDefaultProfile();
        ensureIndexes();
        Collection<String> missingKnowledgeBaseIds = neo4jClient.query("""
            MATCH (document:DocumentUpload)
            WHERE document.knowledgeBaseId IS NOT NULL
              AND NOT EXISTS { MATCH (:KnowledgeBase {id: document.knowledgeBaseId}) }
            RETURN DISTINCT document.knowledgeBaseId AS knowledgeBaseId
            """)
            .fetchAs(String.class)
            .all();
        for (String knowledgeBaseId : missingKnowledgeBaseIds) {
            knowledgeBaseLifecycleService.provision(knowledgeBaseId, "kb-" + knowledgeBaseId);
        }
        log.info("Knowledge base lifecycle migration complete: provisionedKnowledgeBases={}", missingKnowledgeBaseIds.size());
    }

    private void ensureIndexes() {
        neo4jClient.query("CREATE INDEX document_upload_knowledge_base IF NOT EXISTS FOR (d:DocumentUpload) ON (d.knowledgeBaseId)").run();
        neo4jClient.query("CREATE INDEX document_storage_mutation_state IF NOT EXISTS FOR (m:DocumentStorageMutation) ON (m.state)").run();
        neo4jClient.query("CREATE INDEX document_storage_mutation_document IF NOT EXISTS FOR (m:DocumentStorageMutation) ON (m.documentId)").run();
        neo4jClient.query("CREATE INDEX document_storage_mutation_completed IF NOT EXISTS FOR (m:DocumentStorageMutation) ON (m.completedAt)").run();
    }
}
