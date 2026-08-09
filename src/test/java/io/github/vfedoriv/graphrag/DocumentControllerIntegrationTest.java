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
import java.util.HashMap;
import java.util.Map;
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
    void readsBoundedChunksHierarchySummariesDirectChunksAndLegacyList() throws Exception {
        String firstDocumentId = uploadDocument("kb-1", "hierarchy.txt", "first document");
        String secondDocumentId = uploadDocument("kb-2", "flat.txt", "second document");

        createChunk("kb-1", firstDocumentId, "parent-1", 2, "PARENT", null, 1, 2, "parent secret text");
        createChunk("kb-1", firstDocumentId, "a-child", 0, "CHILD", "parent-1", 1, 0, "first child");
        createChunk("kb-1", firstDocumentId, "z-child", 0, "CHILD", "parent-1", 1, 0, "second child");
        createChunk("kb-1", firstDocumentId, "last-child", 1, "CHILD", "parent-1", 1, 0, "last child");
        createChunk("kb-2", secondDocumentId, "flat-2", 2, "CHILD", null, 0, 0, "flat second");
        createChunk("kb-2", secondDocumentId, "flat-1", 1, "CHILD", null, 0, 0, "flat first");
        createChunk("kb-2", secondDocumentId, "foreign-child", 0, "CHILD", null, 0, 0, "foreign child");

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", firstDocumentId)
                .param("page", "0")
                .param("size", "2")
                .param("kind", "child")
                .param("parentChunkId", "parent-1")
                .param("sectionIndex", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].id").value("a-child"))
            .andExpect(jsonPath("$.content[1].id").value("z-child"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", firstDocumentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("parent-1"))
            .andExpect(jsonPath("$.content[0].childCount").value(2))
            .andExpect(jsonPath("$.content[0].text").doesNotExist())
            .andExpect(jsonPath("$.flatChunkCount").value(0));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", secondDocumentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.flatChunkCount").value(3));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/{chunkId}", firstDocumentId, "a-child"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("a-child"))
            .andExpect(jsonPath("$.text").value("first child"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/{chunkId}", firstDocumentId, "foreign-child"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks", firstDocumentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[?(@.id == 'parent-1')].text").value("parent secret text"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", firstDocumentId)
                .param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("page must be non-negative and size must be between 1 and 100"));
    }

    @Test
    void rejectsInvalidChunkReadInputsAndUsesUniformNotFoundResponses() throws Exception {
        String documentId = uploadDocument("kb-1", "validation.txt", "validation document");

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.type").value("about:blank"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("size", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("page must be non-negative and size must be between 1 and 100"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("kind", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("kind must be PARENT, CHILD, or FLAT"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("kind", "PARENT")
                .param("parentChunkId", "parent-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("parentChunkId can only be used with kind=CHILD"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("kind", "FLAT")
                .param("parentChunkId", "parent-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("parentChunkId cannot be used with kind=FLAT"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("sectionIndex", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("sectionIndex must be non-negative"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", documentId).param("parentChunkId", " "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("parentChunkId must not be blank"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("Document not found: missing"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Document not found: missing"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/{chunkId}", "missing", "chunk-1"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Document not found: missing"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/{chunkId}", documentId, "missing-chunk"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Document chunk not found: missing-chunk"));
    }

    @Test
    void exposesEmptyPagesOutOfRangePagesAndParentChildNavigation() throws Exception {
        String hierarchicalDocumentId = uploadDocument("kb-1", "navigation.txt", "hierarchical document");
        String flatDocumentId = uploadDocument("kb-2", "flat-navigation.txt", "flat document");
        String foreignDocumentId = uploadDocument("kb-2", "foreign-navigation.txt", "foreign document");

        createChunk("kb-1", hierarchicalDocumentId, "parent-1", 0, "PARENT", null, 1, 2, "parent one");
        createChunk("kb-1", hierarchicalDocumentId, "a-child", 1, "CHILD", "parent-1", 1, 0, "child a");
        createChunk("kb-1", hierarchicalDocumentId, "z-child", 2, "CHILD", "parent-1", 1, 0, "child z");
        createChunk("kb-1", hierarchicalDocumentId, "parent-2", 5, "PARENT", null, 2, 1, "parent two");
        createChunk("kb-1", hierarchicalDocumentId, "parent-2-child", 6, "CHILD", "parent-2", 2, 0, "child two");
        createChunk("kb-1", hierarchicalDocumentId, "hier-flat-a", 3, "CHILD", null, 2, 0, "flat a");
        createChunk("kb-1", hierarchicalDocumentId, "flat-root", 3, "CHILD", null, 2, 0, "flat root");
        createChunk("kb-1", hierarchicalDocumentId, "flat-later", 7, "CHILD", null, 2, 0, "flat later");
        createChunk("kb-1", hierarchicalDocumentId, "legacy-unsupported", 4, "LEGACY", null, 2, 0, "legacy unsupported");
        createChunk("kb-1", hierarchicalDocumentId, "null-kind", 8, null, null, 2, 0, "null kind");
        createChunk("kb-2", foreignDocumentId, "other-document-parent-child", 1, "CHILD", "parent-1", 1, 0, "not visible");
        createChunk("kb-2", flatDocumentId, "flat-z", 0, "CHILD", null, 0, 0, "flat z");
        createChunk("kb-2", flatDocumentId, "flat-a", 0, "CHILD", null, 0, 0, "flat a");

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", hierarchicalDocumentId)
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value("parent-1"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", hierarchicalDocumentId)
                .param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("parent-2"))
            .andExpect(jsonPath("$.content[0].childCount").value(1));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/hierarchy", hierarchicalDocumentId)
                .param("page", "2").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "CHILD").param("parentChunkId", "parent-1")
                .param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value("a-child"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "CHILD").param("parentChunkId", "parent-1")
                .param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("z-child"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "CHILD").param("parentChunkId", "parent-1")
                .param("page", "2").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "CHILD").param("parentChunkId", "missing-parent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "CHILD").param("sectionIndex", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.content[0].id").value("flat-root"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", " flat ").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].id").value("flat-root"))
            .andExpect(jsonPath("$.content[1].id").value("hier-flat-a"))
            .andExpect(jsonPath("$.content[0].kind").value("CHILD"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "FLAT").param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].id").value("flat-later"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", hierarchicalDocumentId)
                .param("kind", "FLAT").param("sectionIndex", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", flatDocumentId)
                .param("kind", "CHILD").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].id").value("flat-a"));
        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/page", flatDocumentId)
                .param("kind", "CHILD").param("page", "1").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("flat-z"));
    }

    @Test
    void exposesChunkReadRoutesInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/page'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/hierarchy'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/{chunkId}'].get").exists())
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/page'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/page'].get.parameters[?(@.name == 'kind')].description")
                .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("virtual FLAT"))))
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/page'].get.parameters[?(@.name == 'parentChunkId')].description")
                .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("must be omitted when kind=FLAT"))))
            .andExpect(jsonPath("$.paths['/api/v1/documents/{documentId}/chunks/page'].get.responses['200'].content['application/json'].schema.$ref")
                .value("#/components/schemas/DocumentChunkPage"))
            .andExpect(jsonPath("$.components.schemas.DocumentChunkPage.properties.content.items.$ref")
                .value("#/components/schemas/DocumentChunkResponse"))
            .andExpect(jsonPath("$.components.schemas.DocumentChunkResponse.properties.kind.description")
                .value(org.hamcrest.Matchers.containsString("virtual FLAT page requests still return CHILD")));
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

    private String uploadDocument(String knowledgeBaseId, String filename, String content) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                .file(new MockMultipartFile("file", filename, "text/plain", content.getBytes())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void createChunk(
        String knowledgeBaseId,
        String documentId,
        String id,
        int chunkIndex,
        String kind,
        String parentChunkId,
        int sectionIndex,
        int childCount,
        String text
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", id);
        properties.put("documentId", documentId);
        properties.put("knowledgeBaseId", knowledgeBaseId);
        properties.put("processingRunId", "run-1");
        properties.put("chunkIndex", chunkIndex);
        if (kind != null) {
            properties.put("kind", kind);
        }
        properties.put("sectionIndex", sectionIndex);
        properties.put("sectionChunkIndex", chunkIndex);
        properties.put("childCount", childCount);
        properties.put("text", text);
        properties.put("tokenEstimate", 2);
        if (parentChunkId != null) {
            properties.put("parentChunkId", parentChunkId);
        }
        neo4jClient.query("CREATE (:DocumentChunk $properties)")
            .bind(properties).to("properties")
            .run();
    }
}
