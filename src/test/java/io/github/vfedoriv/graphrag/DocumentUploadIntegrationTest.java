package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.service.DocumentUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
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
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "app.storage.documents-root=./target/test-documents"
})
class DocumentUploadIntegrationTest {

    @Autowired
    private DocumentUploadService documentUploadService;
    @Autowired
    private DocumentUploadRepository documentUploadRepository;
    @Autowired
    private Neo4jClient neo4jClient;

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    @Test
    void persistsMetadataAndSkipsDuplicateHashWithinKnowledgeBase() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        MockMultipartFile one = new MockMultipartFile("file", "contract.txt", "text/plain", "same-content".getBytes());
        MockMultipartFile two = new MockMultipartFile("file", "contract-copy.txt", "text/plain", "same-content".getBytes());

        DocumentUploadNode first = documentUploadService.upload("kb-1", one);
        DocumentUploadNode second = documentUploadService.upload("kb-1", two);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getSha256()).isEqualTo(second.getSha256());
        assertThat(first.getContentUri()).startsWith("file:");
        assertThat(documentUploadRepository.findAll()).hasSize(1);
    }
}
