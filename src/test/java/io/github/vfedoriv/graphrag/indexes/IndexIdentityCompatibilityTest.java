package io.github.vfedoriv.graphrag.indexes;

import io.github.vfedoriv.graphrag.indexes.adapters.graph.EmbeddingSpaceIndexService;
import io.github.vfedoriv.graphrag.indexes.adapters.graph.Neo4jLexicalIndexRepository;
import io.github.vfedoriv.graphrag.indexes.contracts.LexicalIndexIdentity;
import io.github.vfedoriv.graphrag.indexes.contracts.VectorIndexes;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.data.neo4j.core.Neo4jClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class IndexIdentityCompatibilityTest {
    @Test
    void persistedVectorPartitionsRetainExactUtf8HashesAndPrefixes() {
        VectorIndexes indexes = new EmbeddingSpaceIndexService(mock(Neo4jClient.class));
        assertThat(indexes.indexName("kb-1", "space-1"))
            .isEqualTo("document_chunk_embedding_d13729073a2e5a76cf0b8480");
        assertThat(indexes.labelName("kb-1", "space-1"))
            .isEqualTo("EmbeddingSpace_d13729073a2e5a76cf0b8480");
        assertThat(indexes.indexName("база", "простір"))
            .isEqualTo("document_chunk_embedding_a47c4e9ec3ddc2035d0e9823");
        assertThat(indexes.indexName("kb-1", "space-1"))
            .isNotEqualTo(indexes.indexName("kb-2", "space-1"))
            .isNotEqualTo(indexes.indexName("kb-1", "space-2"));
    }

    @Test
    void persistedLexicalPartitionsRetainExactHashesAndBlankAdmission() {
        assertThat(LexicalIndexIdentity.indexName("kb-1"))
            .isEqualTo("document_chunk_source_text_5373c3baa7d8a59141181da7");
        assertThat(LexicalIndexIdentity.labelName("kb-1"))
            .isEqualTo("KnowledgeBaseText_5373c3baa7d8a59141181da7");
        assertThatThrownBy(() -> LexicalIndexIdentity.indexName(" "))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("knowledgeBaseId must not be blank");
    }

    @Test
    void expiredLexicalReadinessDoesNotMutateMembershipOrCreateIndexes() {
        Neo4jClient client = mock(Neo4jClient.class);
        Driver driver = mock(Driver.class);
        Neo4jLexicalIndexRepository indexes = new Neo4jLexicalIndexRepository(client, driver);
        assertThatThrownBy(() -> indexes.ensureOnline("kb-1", Instant.EPOCH))
            .isInstanceOf(IllegalStateException.class).hasMessage("Lexical index readiness deadline exceeded");
        assertThatThrownBy(() -> indexes.ensureOnline("kb-1", null))
            .isInstanceOf(IllegalStateException.class).hasMessage("Lexical index readiness deadline exceeded");
        verifyNoInteractions(client, driver);
    }
}
