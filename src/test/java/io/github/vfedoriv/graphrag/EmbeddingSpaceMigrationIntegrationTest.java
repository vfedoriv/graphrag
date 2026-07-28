package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.service.AiProfileService;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceMigrationService;
import io.github.vfedoriv.graphrag.service.KnowledgeBaseLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration"
})
class EmbeddingSpaceMigrationIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private AiProfileService aiProfileService;
    @Autowired
    private EmbeddingSpaceMigrationService migrationService;
    @Autowired
    private EmbeddingSpaceIndexService embeddingSpaceIndexService;
    @Autowired
    private KnowledgeBaseLifecycleService knowledgeBaseLifecycleService;

    @BeforeEach
    void setUp() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        aiProfileService.seedDefaultProfile();
        knowledgeBaseLifecycleService.provision("kb-1", "KB 1");
        neo4jClient.query("""
            CREATE (:DocumentChunk {
              id: 'legacy-compatible', knowledgeBaseId: 'kb-1', documentId: 'doc-1',
              embedding: [1.0, 0.0, 0.0],
              embeddingModel: 'text-embedding-3-small', embeddingDimensions: 1536
            })
            CREATE (:DocumentChunk {
              id: 'legacy-ambiguous', knowledgeBaseId: 'kb-1', documentId: 'doc-1',
              embedding: [0.0, 1.0, 0.0],
              embeddingModel: 'other-model', embeddingDimensions: 1536
            })
            """).run();
    }

    @Test
    void backfillsOnlyUnambiguousLegacyChunksAndCreatesTheirIsolatedIndex() {
        migrationService.run(null);
        AiProfileNode profile = aiProfileService.defaultProfile();
        EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.fromProfile(profile);

        Long assigned = neo4jClient.query("""
            MATCH (chunk:DocumentChunk {id: 'legacy-compatible'})
            WHERE chunk.embeddingSpaceId = $embeddingSpaceId AND chunk:%s
            RETURN count(chunk) AS count
            """.formatted(embeddingSpaceIndexService.labelName("kb-1", embeddingSpace.id())))
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .fetchAs(Long.class).one().orElse(0L);
        Long quarantined = neo4jClient.query("""
            MATCH (chunk:DocumentChunk {id: 'legacy-ambiguous'})
            WHERE chunk.embeddingSpaceId IS NULL
            RETURN count(chunk) AS count
            """)
            .fetchAs(Long.class).one().orElse(0L);
        Long indexCount = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE name = $name AND type = 'VECTOR'
            RETURN count(*) AS count
            """)
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .fetchAs(Long.class).one().orElse(0L);

        assertThat(assigned).isEqualTo(1L);
        assertThat(quarantined).isEqualTo(1L);
        assertThat(indexCount).isEqualTo(1L);
    }
}
