package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.embedding.EmbeddingClient;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.service.DocumentProcessingService;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DocumentProcessingIntegrationTest.FakeEmbeddingConfig.class})
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.autoconfigure.exclude="
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,"
        + "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration,"
        + "org.springframework.ai.vectorstore.neo4j.autoconfigure.Neo4jVectorStoreAutoConfiguration,"
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
class DocumentProcessingIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentProcessingService documentProcessingService;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;
    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    void persistsChunksCreatesVectorIndexAndSupportsVectorSearch() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "contract.txt",
            "text/plain",
            "first chunk sentence. second chunk sentence.".getBytes()
        );
        var uploaded = documentUploadService.upload("kb-1", file);
        var processed = documentProcessingService.process(uploaded.getId());

        assertThat(processed.getStatus().name()).isEqualTo("COMPLETED");
        var chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(uploaded.getId());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks).extracting("chunkIndex").isSorted();
        assertThat(chunks.get(0).getEmbedding()).hasSize(1536);

        Long indexCount = neo4jClient.query("""
            SHOW INDEXES YIELD name, type
            WHERE name = $name AND type = 'VECTOR'
            RETURN count(*) AS c
            """)
            .bind(DocumentProcessingService.CHUNK_EMBEDDING_INDEX).to("name")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(indexCount).isEqualTo(1L);

        Long hitCount = neo4jClient.query("""
            CALL db.index.vector.queryNodes($name, 3, $queryVector) YIELD node, score
            RETURN count(node) AS c
            """)
            .bind(DocumentProcessingService.CHUNK_EMBEDDING_INDEX).to("name")
            .bind(vectorOf(0.11)).to("queryVector")
            .fetchAs(Long.class).one().orElse(0L);
        assertThat(hitCount).isGreaterThan(0L);
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {
        @Bean
        EmbeddingClient embeddingClient() {
            return texts -> {
                List<List<Double>> out = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) {
                    out.add(vectorOf(0.11 + i));
                }
                return out;
            };
        }
    }

    private static List<Double> vectorOf(double base) {
        List<Double> vector = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) {
            vector.add(base + (i * 0.000001));
        }
        return vector;
    }
}
