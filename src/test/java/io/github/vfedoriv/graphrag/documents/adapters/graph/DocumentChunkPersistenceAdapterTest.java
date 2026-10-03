package io.github.vfedoriv.graphrag.documents.adapters.graph;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.vfedoriv.graphrag.documents.domain.chunking.ChunkHashes;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentChunkRepository;
import io.github.vfedoriv.graphrag.indexes.contracts.LexicalIndexRepository;
import io.github.vfedoriv.graphrag.ai.domain.EmbeddingTarget;
import io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.neo4j.core.Neo4jClient;

class DocumentChunkPersistenceAdapterTest {

    private final DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
    private final Neo4jClient neo4jClient = mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS);
    private final DocumentChunkPersistenceAdapter adapter = new DocumentChunkPersistenceAdapter(
        repository,
        mock(VectorIndexes.class),
        mock(LexicalIndexRepository.class),
        neo4jClient
    );
    private final EmbeddingTarget embeddingSpace = new EmbeddingTarget(
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

    @Test
    void rejectsMixedReplacementBeforeDeletingExistingChunks() {
        DocumentChunkNode parent = parent("parent", "revision-a", 0);
        DocumentChunkNode flatChild = child("flat-child", null, "revision-a", null);

        assertThatThrownBy(() -> adapter.replace("doc", "kb", embeddingSpace, List.of(parent, flatChild)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Document chunk topology is invalid");

        verify(repository, never()).deleteByDocumentId("doc");
    }

    @Test
    void acceptsPureFlatReplacement() {
        DocumentChunkNode first = child("flat-1", null, "revision-a", null);
        DocumentChunkNode second = child("flat-2", null, "revision-a", null);

        adapter.replace("doc", "kb", embeddingSpace, List.of(first, second));

        verify(repository).deleteByDocumentId("doc");
        verify(repository).save(first);
        verify(repository).save(second);
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

    private DocumentChunkNode child(String id, String parentId, String revision, Integer childIndex) {
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
