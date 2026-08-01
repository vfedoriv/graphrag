package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.neo4j.core.Neo4jClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class DocumentChunkRepositoryIntegrationTest {

    @Autowired
    private DocumentChunkRepository repository;
    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void clearGraph() {
        neo4jClient.query("MATCH (node) DETACH DELETE node").run();
    }

    @Test
    void pagesFilteredChunksWithStableTieOrderingAndExactTotals() {
        createChunk("doc-1", "parent-1", 2, "PARENT", null, 1, 3);
        createChunk("doc-1", "z-child", 0, "CHILD", "parent-1", 1, 0);
        createChunk("doc-1", "a-child", 0, "CHILD", "parent-1", 1, 0);
        createChunk("doc-1", "last-child", 1, "CHILD", "parent-1", 1, 0);
        createChunk("doc-1", "other-section", 3, "CHILD", "parent-1", 2, 0);
        createChunk("doc-2", "foreign-child", 0, "CHILD", null, 1, 0);
        createChunk("doc-2", "foreign-same-parent", 1, "CHILD", "parent-1", 1, 0);

        Page<DocumentChunkNode> firstPage = repository.findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 1, PageRequest.of(0, 2)
        );
        Page<DocumentChunkNode> secondPage = repository.findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 1, PageRequest.of(1, 2)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("a-child", "z-child");
        assertThat(secondPage.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("last-child");
        Page<DocumentChunkNode> allDocumentChunks = repository.findPageByDocumentId(
            "doc-1", null, null, null, PageRequest.of(0, 20)
        );
        assertThat(allDocumentChunks.getTotalElements()).isEqualTo(5);
        assertThat(allDocumentChunks.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("a-child", "z-child", "last-child", "parent-1", "other-section");
        Page<DocumentChunkNode> emptyFilter = repository.findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 99, PageRequest.of(0, 20)
        );
        assertThat(emptyFilter.getTotalElements()).isZero();
        assertThat(emptyFilter.getContent()).isEmpty();
        Page<DocumentChunkNode> emptyPage = repository.findPageByDocumentId(
            "doc-1", "CHILD", "parent-1", 1, PageRequest.of(2, 2)
        );
        assertThat(emptyPage.getTotalElements()).isEqualTo(3);
        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(repository.findByIdAndDocumentId("a-child", "doc-1")).get()
            .extracting(DocumentChunkNode::getId).isEqualTo("a-child");
        assertThat(repository.findByIdAndDocumentId("foreign-child", "doc-1")).isEmpty();
        assertThat(repository.findByIdAndDocumentId("foreign-same-parent", "doc-1")).isEmpty();
    }

    @Test
    void pagesParentSummariesCountsFlatRootsAndHandlesEmptyDocuments() {
        createChunk("hierarchical", "parent-2", 4, "PARENT", null, 0, 1);
        createChunk("hierarchical", "parent-1", 1, "PARENT", null, 0, 1);
        createChunk("hierarchical", "child-1", 2, "CHILD", "parent-1", 0, 0);
        createChunk("hierarchical", "child-2", 5, "CHILD", "parent-2", 0, 0);
        createChunk("flat", "flat-2", 2, "CHILD", null, 0, 0);
        createChunk("flat", "flat-1", 1, "CHILD", null, 0, 0);

        Page<DocumentChunkNode> parents = repository.findParentPageByDocumentId(
            "hierarchical", PageRequest.of(0, 20)
        );

        assertThat(parents.getTotalElements()).isEqualTo(2);
        assertThat(parents.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("parent-1", "parent-2");
        Page<DocumentChunkNode> firstParentPage = repository.findParentPageByDocumentId(
            "hierarchical", PageRequest.of(0, 1)
        );
        Page<DocumentChunkNode> secondParentPage = repository.findParentPageByDocumentId(
            "hierarchical", PageRequest.of(1, 1)
        );
        Page<DocumentChunkNode> emptyParentPage = repository.findParentPageByDocumentId(
            "hierarchical", PageRequest.of(2, 1)
        );
        assertThat(firstParentPage.getTotalElements()).isEqualTo(2);
        assertThat(firstParentPage.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("parent-1");
        assertThat(secondParentPage.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("parent-2");
        assertThat(emptyParentPage.getContent()).isEmpty();
        assertThat(repository.countFlatChunksByDocumentId("hierarchical")).isZero();
        assertThat(repository.countFlatChunksByDocumentId("flat")).isEqualTo(2);
        assertThat(repository.findParentPageByDocumentId("empty", PageRequest.of(0, 20)).getTotalElements())
            .isZero();
        Page<DocumentChunkNode> flatPage = repository.findPageByDocumentId(
            "flat", "CHILD", null, null, PageRequest.of(1, 1)
        );
        assertThat(flatPage.getTotalElements()).isEqualTo(2);
        assertThat(flatPage.getContent()).extracting(DocumentChunkNode::getId)
            .containsExactly("flat-2");
    }

    @Test
    void pagesLargeDocumentWithoutLoadingMoreThanRequestedRows() {
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (int index = 0; index < 105; index++) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("id", "large-" + index);
            properties.put("documentId", "large-document");
            properties.put("kind", "CHILD");
            properties.put("chunkIndex", index);
            properties.put("sectionIndex", 0);
            properties.put("childCount", 0);
            properties.put("text", "chunk-" + index);
            chunks.add(properties);
        }
        neo4jClient.query("""
            UNWIND $chunks AS properties
            CREATE (chunk:DocumentChunk)
            SET chunk = properties
            """)
            .bind(chunks).to("chunks")
            .run();

        Page<DocumentChunkNode> page = repository.findPageByDocumentId(
            "large-document", "CHILD", null, null, PageRequest.of(1, 100)
        );

        assertThat(page.getTotalElements()).isEqualTo(105);
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getContent()).extracting(DocumentChunkNode::getChunkIndex)
            .containsExactly(100, 101, 102, 103, 104);
    }

    private void createChunk(
        String documentId,
        String id,
        int chunkIndex,
        String kind,
        String parentChunkId,
        int sectionIndex,
        int childCount
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", id);
        properties.put("documentId", documentId);
        properties.put("kind", kind);
        properties.put("chunkIndex", chunkIndex);
        properties.put("sectionIndex", sectionIndex);
        properties.put("childCount", childCount);
        properties.put("text", id + " text");
        if (parentChunkId != null) {
            properties.put("parentChunkId", parentChunkId);
        }
        neo4jClient.query("CREATE (:DocumentChunk $properties)")
            .bind(properties).to("properties")
            .run();
    }
}
