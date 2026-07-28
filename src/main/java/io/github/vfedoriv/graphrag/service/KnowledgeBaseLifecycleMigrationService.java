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
        Collection<String> discoveredKnowledgeBaseIds = neo4jClient.query("""
            MATCH (chunk:DocumentChunk)
            WHERE chunk.knowledgeBaseId IS NOT NULL
            RETURN DISTINCT chunk.knowledgeBaseId AS knowledgeBaseId
            """)
            .fetchAs(String.class)
            .all();
        for (String knowledgeBaseId : discoveredKnowledgeBaseIds) {
            knowledgeBaseLifecycleService.provision(knowledgeBaseId, "kb-" + knowledgeBaseId);
        }
        log.info(
            "Knowledge base lifecycle migration complete: discoveredKnowledgeBases={}",
            discoveredKnowledgeBaseIds.size()
        );
    }

}
