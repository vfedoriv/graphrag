package io.github.vfedoriv.graphrag.service;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import io.github.vfedoriv.graphrag.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@Slf4j
public class EmbeddingSpaceMigrationService implements ApplicationRunner {

    private final AiProfileService aiProfileService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final Neo4jClient neo4jClient;
    private final EmbeddingSpaceIndexService embeddingSpaceIndexService;

    public EmbeddingSpaceMigrationService(
        AiProfileService aiProfileService,
        KnowledgeBaseRepository knowledgeBaseRepository,
        Neo4jClient neo4jClient,
        EmbeddingSpaceIndexService embeddingSpaceIndexService
    ) {
        this.aiProfileService = aiProfileService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.neo4jClient = neo4jClient;
        this.embeddingSpaceIndexService = embeddingSpaceIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long backfilledChunks = 0;
        for (KnowledgeBaseNode knowledgeBase : knowledgeBaseRepository.findAllByOrderByCreatedAtDesc()) {
            AiProfileNode profile = activeProfile(knowledgeBase);
            EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.fromProfile(profile);
            long migrated = backfillUnambiguousChunks(knowledgeBase.getId(), embeddingSpace);
            if (migrated > 0) {
                embeddingSpaceIndexService.ensureIndex(knowledgeBase.getId(), embeddingSpace);
                assignLabel(knowledgeBase.getId(), embeddingSpace);
                backfilledChunks += migrated;
            }
        }
        long quarantinedChunks = countQuarantinedChunks();
        log.info(
            "Embedding-space migration complete: backfilledChunks={}, quarantinedChunks={}, managedVectorIndexes={}",
            backfilledChunks,
            quarantinedChunks,
            embeddingSpaceIndexService.managedIndexCount()
        );
    }

    private AiProfileNode activeProfile(KnowledgeBaseNode knowledgeBase) {
        String profileId = knowledgeBase.getActiveAiProfileId();
        return profileId == null || profileId.isBlank()
            ? aiProfileService.defaultProfile()
            : aiProfileService.getNode(profileId);
    }

    private long backfillUnambiguousChunks(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        Map<String, Object> row = neo4jClient.query("""
            MATCH (chunk:DocumentChunk {knowledgeBaseId: $knowledgeBaseId})
            WHERE chunk.embedding IS NOT NULL
              AND chunk.embeddingSpaceId IS NULL
              AND chunk.embeddingModel = $embeddingModel
              AND chunk.embeddingDimensions = $embeddingDimensions
            SET chunk.embeddingSpaceId = $embeddingSpaceId
            RETURN count(chunk) AS count
            """)
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpace.model()).to("embeddingModel")
            .bind(embeddingSpace.dimensions()).to("embeddingDimensions")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .fetch()
            .one()
            .orElse(Map.of("count", 0L));
        Object count = row.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }

    private void assignLabel(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        neo4jClient.query("""
            MATCH (chunk:DocumentChunk {
              knowledgeBaseId: $knowledgeBaseId,
              embeddingSpaceId: $embeddingSpaceId
            })
            SET chunk:%s
            """.formatted(embeddingSpaceIndexService.labelName(knowledgeBaseId, embeddingSpace.id())))
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .run();
    }

    private long countQuarantinedChunks() {
        Map<String, Object> row = neo4jClient.query("""
            MATCH (chunk:DocumentChunk)
            WHERE chunk.embedding IS NOT NULL AND chunk.embeddingSpaceId IS NULL
            RETURN count(chunk) AS count
            """)
            .fetch()
            .one()
            .orElse(Map.of("count", 0L));
        Object count = row.get("count");
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
