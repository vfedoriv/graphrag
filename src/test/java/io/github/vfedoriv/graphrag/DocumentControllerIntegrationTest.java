package io.github.vfedoriv.graphrag;

import io.github.vfedoriv.graphrag.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void clearGraph() throws Exception {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
        RelationalMetadataTestCleaner.clean(jdbcTemplate);
        createKnowledgeBase("kb-1");
        createKnowledgeBase("kb-2");
        TestDocumentStorage.clean();
    }

    @AfterEach
    void cleanDocumentStorage() throws Exception {
        TestDocumentStorage.clean();
    }

    private void createKnowledgeBase(String id) throws Exception {
        mockMvc.perform(post("/api/v1/knowledge-bases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"name\":\"" + id + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void listsOnlyDocumentsFromRequestedKnowledgeBase() throws Exception {
        MockMultipartFile kb1Doc1 = new MockMultipartFile("file", "a.txt", "text/plain", "alpha".getBytes());
        MockMultipartFile kb1Doc2 = new MockMultipartFile("file", "b.txt", "text/plain", "beta".getBytes());
        MockMultipartFile kb2Doc = new MockMultipartFile("file", "c.txt", "text/plain", "gamma".getBytes());

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(kb1Doc1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.localPath").isNotEmpty());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(kb1Doc2))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-2").file(kb2Doc))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].knowledgeBaseId").value("kb-1"))
            .andExpect(jsonPath("$[0].localPath").isNotEmpty())
            .andExpect(jsonPath("$[1].knowledgeBaseId").value("kb-1"))
            .andExpect(jsonPath("$[1].localPath").isNotEmpty());
    }

    @Test
    void replacesDocumentAndKeepsDocumentId() throws Exception {
        MockMultipartFile original = new MockMultipartFile("file", "original.txt", "text/plain", "alpha".getBytes());
        String uploadBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(original))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode uploaded = objectMapper.readTree(uploadBody);
        String documentId = uploaded.get("id").asText();

        MockMultipartFile replacement = new MockMultipartFile("file", "replacement.txt", "text/plain", "replacement".getBytes());
        String replacementBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", documentId)
                .file(replacement)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(documentId))
            .andExpect(jsonPath("$.knowledgeBaseId").value("kb-1"))
            .andExpect(jsonPath("$.originalFilename").value("replacement.txt"))
            .andExpect(jsonPath("$.status").value("UPLOADED"))
            .andExpect(jsonPath("$.processedAt").doesNotExist())
            .andExpect(jsonPath("$.errorMessage").doesNotExist())
            .andExpect(jsonPath("$.localPath").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode replaced = objectMapper.readTree(replacementBody);

        assertStoredFileContains(replaced.get("localPath").asText(), "replacement");
    }

    @Test
    void rejectsReplacementForMissingMismatchedDuplicateAndInvalidDocuments() throws Exception {
        MockMultipartFile targetFile = new MockMultipartFile("file", "target.txt", "text/plain", "target".getBytes());
        MockMultipartFile duplicateFile = new MockMultipartFile("file", "duplicate.txt", "text/plain", "duplicate".getBytes());
        String targetBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(targetFile))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode target = objectMapper.readTree(targetBody);
        String targetDocumentId = target.get("id").asText();

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1").file(duplicateFile))
            .andExpect(status().isOk());
        String mismatchBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-2")
                .file(new MockMultipartFile("file", "other.txt", "text/plain", "other".getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String mismatchedDocumentId = objectMapper.readTree(mismatchBody).get("id").asText();

        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", "missing")
                .file(new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes()))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", mismatchedDocumentId)
                .file(new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes()))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", targetDocumentId)
                .file(new MockMultipartFile("file", "copy.txt", "text/plain", "duplicate".getBytes()))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", targetDocumentId)
                .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                }))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.id == '" + targetDocumentId + "')].originalFilename").value("target.txt"));
    }

    @Test
    void managesDocumentProcessingOptionsDefaultsAndRejectsInvalidInputs() throws Exception {
        String uploadBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1")
                .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", "pdf".getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String documentId = objectMapper.readTree(uploadBody).get("id").asText();

        mockMvc.perform(get("/api/v1/documents/{documentId}/processing-options", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parserId").value("tika"))
            .andExpect(jsonPath("$.fileFormat").value("PDF"))
            .andExpect(jsonPath("$.options[?(@.key == 'ocrEnabled')].defaultValue").value(false));

        mockMvc.perform(put("/api/v1/documents/{documentId}/processing-options/defaults", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "options": { "ocrEnabled": true, "maxPages": 2 } }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedDefaults.ocrEnabled").value(true))
            .andExpect(jsonPath("$.savedDefaults.maxPages").value(2))
            .andExpect(jsonPath("$.savedDefaultsUpdatedAt").isNotEmpty());

        mockMvc.perform(put("/api/v1/documents/{documentId}/processing-options/defaults", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "options": { "ocrEnabled": "yes" } }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0]").value("ocrEnabled must be a boolean"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/processing-options", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedDefaults.ocrEnabled").value(true));

        mockMvc.perform(delete("/api/v1/documents/{documentId}/processing-options/defaults", documentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.savedDefaults").isEmpty())
            .andExpect(jsonPath("$.savedDefaultsUpdatedAt").doesNotExist());
    }

    @Test
    void rejectsConflictingAllowOverwriteBeforeProcessingStarts() throws Exception {
        mockMvc.perform(post("/api/v1/documents/{documentId}/process", "missing")
                .param("allowOverwrite", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "allowOverwrite": false, "options": {} }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("allowOverwrite query parameter conflicts with request body"));
    }

    @Test
    void deletesDocumentAndRejectsMissingMismatchedAndStorageFailureDeletes() throws Exception {
        String firstBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1")
                .file(new MockMultipartFile("file", "delete.txt", "text/plain", "delete".getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode first = objectMapper.readTree(firstBody);
        String documentId = first.get("id").asText();
        String localPath = first.get("localPath").asText();

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", documentId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
        org.assertj.core.api.Assertions.assertThat(Path.of(localPath)).doesNotExist();

        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", "missing"))
            .andExpect(status().isNotFound());

        String mismatchBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-2")
                .file(new MockMultipartFile("file", "other.txt", "text/plain", "other".getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String mismatchedDocumentId = objectMapper.readTree(mismatchBody).get("id").asText();
        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", mismatchedDocumentId))
            .andExpect(status().isNotFound());

        String failureBody = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", "kb-1")
                .file(new MockMultipartFile("file", "missing-binary.txt", "text/plain", "content".getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode failureDocument = objectMapper.readTree(failureBody);
        Files.delete(Path.of(failureDocument.get("localPath").asText()));
        mockMvc.perform(delete("/api/v1/knowledge-bases/{knowledgeBaseId}/documents/{documentId}", "kb-1", failureDocument.get("id").asText()))
            .andExpect(status().isInternalServerError());
    }

    private void assertStoredFileContains(String localPath, String expectedContent) throws Exception {
        org.assertj.core.api.Assertions.assertThat(Files.readString(Path.of(localPath))).isEqualTo(expectedContent);
    }
}
