package io.github.vfedoriv.graphrag.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.vfedoriv.graphrag.document.chunking.ChunkHashes;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.repository.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.repository.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.service.EmbeddingSpace;
import io.github.vfedoriv.graphrag.service.EmbeddingSpaceIndexService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

class DocumentChunkPersistenceAdapterTest {

    private final DocumentChunkPersistenceAdapter adapter = new DocumentChunkPersistenceAdapter(
        mock(DocumentChunkRepository.class),
        mock(EmbeddingSpaceIndexService.class),
        mock(LexicalIndexRepository.class),
        mock(Neo4jClient.class)
    );
    private final EmbeddingSpace embeddingSpace = new EmbeddingSpace(
        "space", "provider", "model", 3, "tokenizer"
    );

    @Test
    void rejectsMixedRevisionChildReferences() {
        DocumentChunkNode parent = parent("parent", "revision-a", 1);
        DocumentChunkNode child = child("child", "parent", "revision-b", 0);

        assertThatThrownBy(() -> adapter.replace("doc", "kb", embeddingSpace, List.of(parent, child)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("revision");
    }

    @Test
    void rejectsNonConsecutiveChildOrdering() {
        DocumentChunkNode parent = parent("parent", "revision-a", 1);
        DocumentChunkNode child = child("child", "parent", "revision-a", 1);

        assertThatThrownBy(() -> adapter.replace("doc", "kb", embeddingSpace, List.of(parent, child)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("consecutive");
    }

    private DocumentChunkNode parent(String id, String revision, int childCount) {
        DocumentChunkNode node = base(id, revision);
        node.setKind("PARENT");
        node.setText("authoritative text");
        node.setSourceHash(ChunkHashes.sha256(node.getText()));
        node.setSourceStart(0);
        node.setSourceEnd(node.getText().length());
        node.setPageStart(1);
        node.setPageEnd(1);
        node.setChildCount(childCount);
        return node;
    }

    private DocumentChunkNode child(String id, String parentId, String revision, int childIndex) {
        DocumentChunkNode node = base(id, revision);
        node.setKind("CHILD");
        node.setParentChunkId(parentId);
        node.setChildIndex(childIndex);
        node.setSourceStart(0);
        node.setSourceEnd(4);
        node.setPageStart(1);
        node.setPageEnd(1);
        node.setEmbedding(List.of(0.1, 0.2, 0.3));
        node.setEmbeddingSpaceId("space");
        return node;
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
