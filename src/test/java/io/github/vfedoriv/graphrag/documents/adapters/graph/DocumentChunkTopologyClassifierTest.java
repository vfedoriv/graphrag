package io.github.vfedoriv.graphrag.documents.adapters.graph;

import io.github.vfedoriv.graphrag.documents.domain.chunking.DocumentChunkTopology;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkTopologyClassifierTest {

    private final DocumentChunkTopologyClassifier classifier = new DocumentChunkTopologyClassifier(null);

    @Test
    void classifiesEmptyPopulation() {
        assertThat(classifier.classify(List.of())).isEqualTo(DocumentChunkTopology.EMPTY);
    }

    @Test
    void acceptsPureFlatPopulation() {
        assertThat(classifier.classify(List.of(
            child("flat-1", null, "revision-a"),
            child("flat-2", null, "revision-a")
        ))).isEqualTo(DocumentChunkTopology.FLAT);
    }

    @Test
    void acceptsPureHierarchyPopulation() {
        DocumentChunkNode parent = parent("parent", "revision-a");
        DocumentChunkNode child = child("child", "parent", "revision-a");

        assertThat(classifier.classify(List.of(parent, child))).isEqualTo(DocumentChunkTopology.HIERARCHICAL);
    }

    @Test
    void rejectsMixedPopulation() {
        assertThat(classifier.classify(List.of(
            parent("parent", "revision-a"),
            child("hierarchical-child", "parent", "revision-a"),
            child("flat-child", null, "revision-a")
        ))).isEqualTo(DocumentChunkTopology.INVALID);
    }

    @Test
    void rejectsOrphanAndCrossScopeReferences() {
        assertThat(classifier.classify(List.of(child("orphan", "missing-parent", "revision-a"))))
            .isEqualTo(DocumentChunkTopology.INVALID);

        DocumentChunkNode parent = parent("parent", "revision-a");
        DocumentChunkNode child = child("child", "parent", "revision-b");
        assertThat(classifier.classify(List.of(parent, child))).isEqualTo(DocumentChunkTopology.INVALID);
    }

    @Test
    void rejectsUnsupportedKinds() {
        DocumentChunkNode unsupported = child("legacy", null, "revision-a");
        unsupported.setKind("LEGACY");

        assertThat(classifier.classify(List.of(unsupported))).isEqualTo(DocumentChunkTopology.INVALID);
    }

    private DocumentChunkNode parent(String id, String revision) {
        DocumentChunkNode parent = base(id, revision);
        parent.setKind("PARENT");
        return parent;
    }

    private DocumentChunkNode child(String id, String parentId, String revision) {
        DocumentChunkNode child = base(id, revision);
        child.setKind("CHILD");
        child.setParentChunkId(parentId);
        return child;
    }

    private DocumentChunkNode base(String id, String revision) {
        DocumentChunkNode node = new DocumentChunkNode();
        node.setId(id);
        node.setKnowledgeBaseId("kb");
        node.setDocumentId("doc");
        node.setProcessingRunId("run");
        node.setEffectiveChunkerRevision(revision);
        return node;
    }
}
