package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.dto.HybridSearchRequest;
import io.github.vfedoriv.graphrag.dto.HybridSearchResponse;
import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIdentity;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.service.HybridSearchService;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({TestcontainersConfiguration.class, HybridSearchIntegrationTest.FakeEmbeddingConfig.class})
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration",
    "app.model.embedding-dimensions=3",
    "app.query.hybrid-search-default-top-k=2",
    "app.query.hybrid-search-max-top-k=5",
    "app.query.hybrid-search-candidate-multiplier=4",
    "app.query.hybrid-search-max-candidates=10",
    "app.query.hybrid-search-default-graph-depth=1",
    "app.query.hybrid-search-max-graph-depth=2",
    "app.query.hybrid-search-include-chunk-text=true"
})
class HybridSearchIntegrationTest {

    @Autowired
    private HybridSearchService hybridSearchService;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private EmbeddingSpaceIndexService embeddingSpaceIndexService;

    @BeforeEach
    void setUpGraph() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        neo4jClient.query("""
            CREATE (:AiProfile {
              id: 'default',
              name: 'Default test profile',
              baseUrl: 'https://api.openai.com/v1',
              apiKey: '',
              chatModel: 'gpt-5-mini',
              embeddingModel: 'text-embedding-3-small',
              embeddingDimensions: 3,
              timeoutSeconds: 60,
              maxRetries: 2,
              defaultProfile: true,
              revision: 1,
              createdAt: datetime(),
              updatedAt: datetime()
            })
            CREATE (:KnowledgeBase {id: 'kb-1', name: 'KB 1', activeAiProfileId: 'default', createdAt: datetime()})
            CREATE (:KnowledgeBase {id: 'kb-2', name: 'KB 2', activeAiProfileId: 'default', createdAt: datetime()})
            CREATE (:KnowledgeBase {id: 'kb-empty', name: 'Empty KB', activeAiProfileId: 'default', createdAt: datetime()})
            CREATE (doc1:DocumentUpload {id: 'doc-1', knowledgeBaseId: 'kb-1', originalFilename: 'contract-a.txt', contentType: 'text/plain', sizeBytes: 100})
            CREATE (doc2:DocumentUpload {id: 'doc-2', knowledgeBaseId: 'kb-1', originalFilename: 'contract-b.txt', contentType: 'text/plain', sizeBytes: 110})
            CREATE (doc3:DocumentUpload {id: 'doc-3', knowledgeBaseId: 'kb-1', originalFilename: 'maintenance.txt', contentType: 'text/plain', sizeBytes: 120})
            CREATE (doc4:DocumentUpload {id: 'doc-4', knowledgeBaseId: 'kb-2', originalFilename: 'other-kb.txt', contentType: 'text/plain', sizeBytes: 130})
            CREATE (exact:DocumentChunk {id: 'chunk-renewal-exact', documentId: 'doc-1', chunkIndex: 0, text: 'Acme renewal terms exact match', metadata: '{"source":"contract-a.txt","section":"renewal"}', embedding: [1.0, 0.0, 0.0]})
            CREATE (near:DocumentChunk {id: 'chunk-renewal-near', documentId: 'doc-1', chunkIndex: 1, text: 'Acme renewal notice and extension terms', metadata: '{"source":"contract-a.txt","section":"notice"}', embedding: [0.95, 0.05, 0.0]})
            CREATE (related:DocumentChunk {id: 'chunk-service-related', documentId: 'doc-2', chunkIndex: 0, text: 'Supplier service renewal obligations', metadata: '{"source":"contract-b.txt","section":"service"}', embedding: [0.8, 0.2, 0.0]})
            CREATE (maintenance:DocumentChunk {id: 'chunk-maintenance-low', documentId: 'doc-3', chunkIndex: 0, text: 'Pump maintenance schedule unrelated to renewal', metadata: '{"source":"maintenance.txt","section":"schedule"}', embedding: [0.2, 0.8, 0.0]})
            CREATE (noise:DocumentChunk {id: 'chunk-noise', documentId: 'doc-3', chunkIndex: 1, text: 'Safety inspection checklist', metadata: '{"source":"maintenance.txt","section":"safety"}', embedding: [0.0, 1.0, 0.0]})
            CREATE (otherKbBest:DocumentChunk {id: 'chunk-other-kb-best', documentId: 'doc-4', chunkIndex: 0, text: 'Other KB renewal exact match', metadata: '{"source":"other-kb.txt","section":"renewal"}', embedding: [1.0, 0.0, 0.0]})
            CREATE (otherKbNear:DocumentChunk {id: 'chunk-other-kb-near', documentId: 'doc-4', chunkIndex: 1, text: 'Other KB renewal near match', metadata: '{"source":"other-kb.txt","section":"notice"}', embedding: [0.9, 0.1, 0.0]})
            CREATE (contract:Contract {id: 'contract-1', contractId: 'C-1', title: 'Master Agreement'})
            CREATE (party:Party {id: 'party-1', name: 'Acme'})
            CREATE (obligation:Obligation {id: 'obligation-1', summary: 'Renewal notice must be sent 30 days before expiry'})
            CREATE (doc1)-[:HAS_CHUNK]->(exact)
            CREATE (doc1)-[:HAS_CHUNK]->(near)
            CREATE (doc2)-[:HAS_CHUNK]->(related)
            CREATE (doc3)-[:HAS_CHUNK]->(maintenance)
            CREATE (doc3)-[:HAS_CHUNK]->(noise)
            CREATE (doc4)-[:HAS_CHUNK]->(otherKbBest)
            CREATE (doc4)-[:HAS_CHUNK]->(otherKbNear)
            CREATE (exact)-[:MENTIONS]->(contract)
            CREATE (near)-[:MENTIONS]->(obligation)
            CREATE (related)-[:MENTIONS]->(party)
            CREATE (contract)-[:HAS_PARTY {role: 'Supplier'}]->(party)
            CREATE (contract)-[:HAS_OBLIGATION {kind: 'Renewal'}]->(obligation)
            """).run();
        EmbeddingSpace embeddingSpace = EmbeddingSpaceIdentity.derive(
            "https://api.openai.com/v1", "text-embedding-3-small", 3
        );
        assignSpace("kb-1", embeddingSpace);
        assignSpace("kb-2", embeddingSpace);
        embeddingSpaceIndexService.ensureIndex("kb-1", embeddingSpace);
        embeddingSpaceIndexService.ensureIndex("kb-2", embeddingSpace);
        neo4jClient.query("CALL db.awaitIndex($name)")
            .bind(embeddingSpaceIndexService.indexName("kb-1", embeddingSpace.id())).to("name")
            .run();
        neo4jClient.query("CALL db.awaitIndex($name)")
            .bind(embeddingSpaceIndexService.indexName("kb-2", embeddingSpace.id())).to("name")
            .run();
    }

    private void assignSpace(String knowledgeBaseId, EmbeddingSpace embeddingSpace) {
        neo4jClient.query("""
            MATCH (:DocumentUpload {knowledgeBaseId: $knowledgeBaseId})-[:HAS_CHUNK]->(chunk:DocumentChunk)
            SET chunk.embeddingSpaceId = $embeddingSpaceId
            SET chunk:%s
            """.formatted(embeddingSpaceIndexService.labelName(knowledgeBaseId, embeddingSpace.id())))
            .bind(knowledgeBaseId).to("knowledgeBaseId")
            .bind(embeddingSpace.id()).to("embeddingSpaceId")
            .run();
    }

    @Test
    void hybridSearchRanksKnowledgeBaseScopedChunksByVectorScore() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-1",
            new HybridSearchRequest("renewal terms", 3, 1, true)
        );

        assertThat(response.hitCount()).isEqualTo(3);
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .containsExactly("chunk-renewal-exact", "chunk-renewal-near", "chunk-service-related");
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .doesNotContain("chunk-other-kb-best", "chunk-other-kb-near", "chunk-maintenance-low", "chunk-noise");
        assertThat(response.hits())
            .extracting(hit -> hit.score())
            .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(response.hits().getFirst().source().originalFilename()).isEqualTo("contract-a.txt");
        assertThat(response.hits().getFirst().text()).isEqualTo("Acme renewal terms exact match");
        assertThat(response.hits().getFirst().graph().entities())
            .anySatisfy(entity -> assertThat(entity.labels()).contains("Contract"));
        assertThat(response.hits().getFirst().graph().relationships())
            .anySatisfy(relationship -> assertThat(relationship.type()).isEqualTo("HAS_PARTY"));
    }

    @Test
    void hybridSearchCanReturnLowerRankedTargetChunksWhenTopVectorHitsBelongToAnotherKnowledgeBase() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-1",
            new HybridSearchRequest("renewal terms", 4, 1, false)
        );

        assertThat(response.hitCount()).isEqualTo(4);
        assertThat(response.includeChunkText()).isFalse();
        assertThat(response.hits())
            .extracting(hit -> hit.chunkId())
            .containsExactly(
                "chunk-renewal-exact",
                "chunk-renewal-near",
                "chunk-service-related",
                "chunk-maintenance-low"
            );
        assertThat(response.hits())
            .extracting(hit -> hit.text())
            .containsOnlyNulls();
    }

    @Test
    void hybridSearchReturnsEmptyHitsForKnowledgeBaseWithoutMatches() {
        HybridSearchResponse response = hybridSearchService.search(
            "kb-empty",
            new HybridSearchRequest("renewal terms", 2, 1, true)
        );

        assertThat(response.hits()).isEmpty();
        assertThat(response.hitCount()).isZero();
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> List.of(List.of(1.0, 0.0, 0.0));
        }
    }
}
