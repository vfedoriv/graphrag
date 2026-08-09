package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkTopology;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkTopologyAuditRow;
import io.github.vfedoriv.graphrag.infrastructure.persistence.DocumentChunkPersistenceAdapter;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
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
    private DocumentChunkPersistenceAdapter chunkPersistenceAdapter;
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
        createRelationship("doc-1", "parent-1", "z-child");
        createRelationship("doc-1", "parent-1", "a-child");
        createRelationship("doc-1", "parent-1", "last-child");
        createRelationship("doc-1", "parent-1", "other-section");
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
    void pagesParentSummariesAndPureFlatCounts() {
        createChunk("hierarchical", "parent-2", 4, "PARENT", null, 0, 1);
        createChunk("hierarchical", "parent-1", 1, "PARENT", null, 0, 1);
        createChunk("hierarchical", "child-1", 2, "CHILD", "parent-1", 0, 0);
        createChunk("hierarchical", "child-2", 5, "CHILD", "parent-2", 0, 0);
        createRelationship("hierarchical", "parent-1", "child-1");
        createRelationship("hierarchical", "parent-2", "child-2");
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

        Page<DocumentChunkNode> hierarchicalFlatPage = repository.findFlatPageByDocumentId(
            "hierarchical", null, PageRequest.of(0, 20)
        );
        assertThat(hierarchicalFlatPage.getTotalElements()).isZero();
        assertThat(hierarchicalFlatPage.getContent()).isEmpty();
        Page<DocumentChunkNode> allChildren = repository.findPageByDocumentId(
            "hierarchical", "CHILD", null, null, PageRequest.of(0, 20)
        );
        assertThat(allChildren.getTotalElements()).isEqualTo(2);
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

    @Test
    void auditsPureFlatAndHierarchicalDocumentsWithNoInvalidRows() {
        createChunk("audit-flat", "flat-1", 0, "CHILD", null, 0, 0);
        createChunk("audit-hierarchy", "parent-1", 0, "PARENT", null, 0, 1);
        createChunk("audit-hierarchy", "child-1", 1, "CHILD", "parent-1", 0, 0);
        createRelationship("audit-hierarchy", "parent-1", "child-1");

        List<DocumentChunkTopologyAuditRow> rows = chunkPersistenceAdapter.auditDocumentTopologies();

        assertThat(rows).extracting(DocumentChunkTopologyAuditRow::topology)
            .containsExactlyInAnyOrder(DocumentChunkTopology.FLAT, DocumentChunkTopology.HIERARCHICAL);
        assertThat(rows).allMatch(row -> !row.invalid());
    }

    @Test
    void auditReportsInvalidPopulationCountsWithoutMutatingIt() {
        createChunk("audit-invalid", "parent-1", 0, "PARENT", null, 0, 0);
        createChunk("audit-invalid", "flat-1", 1, "CHILD", null, 0, 0);

        List<DocumentChunkTopologyAuditRow> rows = chunkPersistenceAdapter.auditDocumentTopologies();

        DocumentChunkTopologyAuditRow row = rows.stream()
            .filter(candidate -> "audit-invalid".equals(candidate.documentId()))
            .findFirst()
            .orElseThrow();
        assertThat(row.topology()).isEqualTo(DocumentChunkTopology.INVALID);
        assertThat(row.parentCount()).isEqualTo(1L);
        assertThat(row.unparentedChildCount()).isEqualTo(1L);
    }

    @Test
    void replacementTransitionsBetweenFlatAndHierarchyWithoutStaleTopology() {
        EmbeddingSpace embeddingSpace = new EmbeddingSpace("space", "provider", "model", 3, "tokenizer");
        DocumentChunkNode firstFlat = transitionChunk("flat-1", "CHILD", null, null, 0);
        chunkPersistenceAdapter.replace("transition-doc", "kb-1", embeddingSpace, List.of(firstFlat));
        assertThat(chunkPersistenceAdapter.classifyDocumentTopology("transition-doc"))
            .isEqualTo(DocumentChunkTopology.FLAT);

        DocumentChunkNode parent = transitionChunk("parent-1", "PARENT", null, null, 0);
        parent.setText("parent text");
        parent.setSourceHash(io.github.vfedoriv.graphrag.document.chunking.ChunkHashes.sha256(parent.getText()));
        parent.setChildCount(1);
        DocumentChunkNode child = transitionChunk("child-1", "CHILD", "parent-1", 0, 1);
        chunkPersistenceAdapter.replace("transition-doc", "kb-1", embeddingSpace, List.of(parent, child));
        assertThat(chunkPersistenceAdapter.classifyDocumentTopology("transition-doc"))
            .isEqualTo(DocumentChunkTopology.HIERARCHICAL);
        assertThat(repository.findByDocumentIdOrderByChunkIndexAsc("transition-doc"))
            .extracting(DocumentChunkNode::getId)
            .containsExactly("parent-1", "child-1");

        DocumentChunkNode secondFlat = transitionChunk("flat-2", "CHILD", null, null, 0);
        chunkPersistenceAdapter.replace("transition-doc", "kb-1", embeddingSpace, List.of(secondFlat));

        assertThat(chunkPersistenceAdapter.classifyDocumentTopology("transition-doc"))
            .isEqualTo(DocumentChunkTopology.FLAT);
        assertThat(repository.findByDocumentIdOrderByChunkIndexAsc("transition-doc"))
            .extracting(DocumentChunkNode::getId)
            .containsExactly("flat-2");
        assertThat(neo4jClient.query("MATCH ()-[relationship:HAS_CHILD]->() RETURN count(relationship) AS count")
            .fetchAs(Long.class).one().orElse(0L)).isZero();
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
        properties.put("knowledgeBaseId", "kb-1");
        properties.put("processingRunId", "run-1");
        properties.put("effectiveChunkerRevision", "chunker-test-v1");
        if (kind != null) {
            properties.put("kind", kind);
        }
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

    private void createRelationship(String documentId, String parentChunkId, String childChunkId) {
        neo4jClient.query("""
            MATCH (parent:DocumentChunk {documentId: $documentId, id: $parentChunkId})
            MATCH (child:DocumentChunk {documentId: $documentId, id: $childChunkId})
            MERGE (parent)-[:HAS_CHILD]->(child)
            """)
            .bind(documentId).to("documentId")
            .bind(parentChunkId).to("parentChunkId")
            .bind(childChunkId).to("childChunkId")
            .run();
    }

    private DocumentChunkNode transitionChunk(
        String id,
        String kind,
        String parentChunkId,
        Integer childIndex,
        int chunkIndex
    ) {
        DocumentChunkNode chunk = new DocumentChunkNode();
        chunk.setId(id);
        chunk.setKnowledgeBaseId("kb-1");
        chunk.setDocumentId("transition-doc");
        chunk.setProcessingRunId("run-1");
        chunk.setEffectiveChunkerRevision("revision-1");
        chunk.setKind(kind);
        chunk.setParentChunkId(parentChunkId);
        chunk.setChildIndex(childIndex);
        chunk.setChunkIndex(chunkIndex);
        chunk.setText(id + " text");
        chunk.setSourceStart(0);
        chunk.setSourceEnd(chunk.getText().length());
        return chunk;
    }
}
