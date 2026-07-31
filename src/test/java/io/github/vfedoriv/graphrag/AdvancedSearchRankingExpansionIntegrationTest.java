package io.github.vfedoriv.graphrag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.CitationKind;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceCandidate;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.EvidenceSource;
import io.github.vfedoriv.graphrag.domain.AdvancedSearchRankingContracts.SourceBounds;
import io.github.vfedoriv.graphrag.service.AdvancedSearchGraphExpansionService;
import io.github.vfedoriv.graphrag.service.AdvancedSearchParentContextService;
import io.github.vfedoriv.graphrag.service.AdvancedSearchParentContextService.ExpansionOptions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@IntegrationTest
class AdvancedSearchRankingExpansionIntegrationTest {

    @Autowired private Neo4jClient neo4jClient;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private AdvancedSearchGraphExpansionService graphExpansionService;
    @Autowired private AdvancedSearchParentContextService parentContextService;

    @BeforeEach
    void setUp() throws Exception {
        IntegrationTestLifecycle.reset(jdbcTemplate, neo4jClient);
        neo4jClient.query("""
            CREATE (parent:DocumentChunk {
              id: 'parent-1', kind: 'PARENT', knowledgeBaseId: 'kb-1', documentId: 'doc-1',
              processingRunId: 'run-1', effectiveChunkerRevision: 'revision-1',
              text: 'bounded parent synthesis context', tokenEstimate: 8,
              sourceStart: 0, sourceEnd: 80, pageStart: 2, pageEnd: 3, structuralPath: 'Terms'
            })
            CREATE (child:DocumentChunk {
              id: 'child-1', kind: 'CHILD', knowledgeBaseId: 'kb-1', documentId: 'doc-1', chunkIndex: 1,
              parentChunkId: parent.id, processingRunId: 'run-1', effectiveChunkerRevision: 'revision-1',
              text: 'precise child evidence', sourceStart: 20, sourceEnd: 40,
              pageStart: 2, pageEnd: 2, structuralPath: 'Terms'
            })
            CREATE (parent)-[:HAS_CHILD]->(child)
            CREATE (fact:Contract {id: 'contract-1', contractId: 'C-1'})
            CREATE (evidence:GraphExtractionEvidence {
              id: 'evidence-1', canonicalFactId: 'contract-1', factKind: 'NODE', schemaType: 'Contract',
              knowledgeBaseId: 'kb-1', sourceChunkId: parent.id, sourceDocumentId: 'doc-1',
              processingRunId: 'run-1', effectiveChunkerRevision: 'revision-1',
              sourceStart: 0, sourceEnd: 80, pageStart: 2, pageEnd: 3, structuralPath: 'Terms'
            })
            CREATE (parent)-[:HAS_GRAPH_EVIDENCE]->(evidence)
            CREATE (evidence)-[:ASSERTS_NODE]->(fact)

            CREATE (missing:DocumentChunk {
              id: 'child-missing-parent', kind: 'CHILD', knowledgeBaseId: 'kb-1', documentId: 'doc-2', chunkIndex: 5,
              parentChunkId: 'absent-parent', processingRunId: 'run-2', effectiveChunkerRevision: 'revision-2',
              text: 'missing parent child', sectionIndex: 1, structuralPath: 'Fallback',
              sourceStart: 100, sourceEnd: 120, pageStart: 5, pageEnd: 5
            })
            CREATE (adjacent:DocumentChunk {
              id: 'child-adjacent', kind: 'CHILD', knowledgeBaseId: 'kb-1', documentId: 'doc-2', chunkIndex: 6,
              parentChunkId: 'absent-parent', processingRunId: 'run-2', effectiveChunkerRevision: 'revision-2',
              text: 'adjacent fallback context', tokenEstimate: 6, sectionIndex: 1, structuralPath: 'Fallback',
              sourceStart: 121, sourceEnd: 150, pageStart: 5, pageEnd: 5
            })
            """).run();
    }

    @Test
    void expandsOnlyAuthoritativeEvidenceAndPreservesParentSourceBounds() {
        AdvancedSearchGraphExpansionService.ExpansionResult result = graphExpansionService.expand(
            "kb-1", List.of(candidate("child-1", "doc-1", 1, "run-1", "revision-1")), 10, 10
        );

        assertThat(result.candidates().getFirst().graphFacts()).singleElement().satisfies(fact -> {
            assertThat(fact.canonicalFactId()).isEqualTo("contract-1");
            assertThat(fact.citations()).singleElement().satisfies(citation -> {
                assertThat(citation.chunkId()).isEqualTo("parent-1");
                assertThat(citation.sourceStart()).isZero();
                assertThat(citation.sourceEnd()).isEqualTo(80);
                assertThat(citation.pageStart()).isEqualTo(2);
                assertThat(citation.pageEnd()).isEqualTo(3);
            });
        });
    }

    @Test
    void usesValidatedParentAndMissingParentAdjacencyWithoutChangingChildCitationIdentity() {
        List<EvidenceCandidate> candidates = List.of(
            candidate("child-1", "doc-1", 1, "run-1", "revision-1"),
            candidate("child-missing-parent", "doc-2", 2, "run-2", "revision-2")
        );

        AdvancedSearchParentContextService.ExpansionResult result = parentContextService.expand(
            "kb-1", candidates, new ExpansionOptions(10, 10, 10, 100, 1, true)
        );

        assertThat(result.candidates()).extracting(EvidenceCandidate::citationKind)
            .containsOnly(CitationKind.TEXT_CHILD);
        assertThat(result.candidates().get(0).parentContext().kind()).isEqualTo("PARENT");
        assertThat(result.candidates().get(0).parentContext().contextChunkId()).isEqualTo("parent-1");
        assertThat(result.candidates().get(1).parentContext().kind()).isEqualTo("ADJACENT");
        assertThat(result.candidates().get(1).parentContext().contributingChunkIds())
            .containsExactly("child-missing-parent", "child-adjacent");
    }

    private EvidenceCandidate candidate(
        String chunkId,
        String documentId,
        int rank,
        String processingRunId,
        String revision
    ) {
        return new EvidenceCandidate(
            new EvidenceSource(
                chunkId,
                documentId,
                rank,
                new SourceBounds(10, 20, 1, 1),
                processingRunId,
                revision,
                "section"
            ),
            CitationKind.TEXT_CHILD,
            "text",
            List.of(),
            List.of(),
            null,
            1.0 / (60 + rank),
            rank,
            null
        );
    }
}
