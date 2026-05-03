package io.github.vfedoriv.graphrag;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
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
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Neo4jClient neo4jClient;

    @Test
    void listsOnlyDocumentsFromRequestedKnowledgeBase() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();

        MockMultipartFile kb1Doc1 = new MockMultipartFile("file", "a.txt", "text/plain", "alpha".getBytes());
        MockMultipartFile kb1Doc2 = new MockMultipartFile("file", "b.txt", "text/plain", "beta".getBytes());
        MockMultipartFile kb2Doc = new MockMultipartFile("file", "c.txt", "text/plain", "gamma".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(kb1Doc1))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(kb1Doc2))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-2").file(kb2Doc))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].knowledgeBaseId").value("kb-1"))
            .andExpect(jsonPath("$[1].knowledgeBaseId").value("kb-1"));
    }
}
