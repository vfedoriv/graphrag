package io.github.vfedoriv.graphrag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CypherParsingSupportTest {

    @Test
    void shouldExtractAliasedRelationshipUnionAndIgnorePropertyMap() {
        String cypher = "MATCH (c:Contract)-[r:`HAS_PARTY`|HAS_SIGNER {extra: 1}]->(p:Party) RETURN c.contractId";

        assertThat(CypherParsingSupport.extractNodeLabels(cypher)).containsExactlyInAnyOrder("Contract", "Party");
        assertThat(CypherParsingSupport.extractRelationshipTypes(cypher)).containsExactly("HAS_PARTY", "HAS_SIGNER");
        assertThat(CypherParsingSupport.extractPropertyReferences(cypher)).containsExactly("contractId");
    }

    @Test
    void shouldIgnoreRelationshipRangeAndKeepTypeBeforeRange() {
        String cypher = "MATCH (a:User)-[:`KNOWS`*1..3]->(b:User) RETURN a.name, b.name";

        assertThat(CypherParsingSupport.extractRelationshipTypes(cypher)).containsExactly("KNOWS");
        assertThat(CypherParsingSupport.extractPropertyReferences(cypher)).containsExactly("name", "name");
    }

    @Test
    void shouldReturnEmptyWhenNoLabelsOrRelationships() {
        String cypher = "RETURN 1";

        assertThat(CypherParsingSupport.extractNodeLabels(cypher)).isEmpty();
        assertThat(CypherParsingSupport.extractRelationshipTypes(cypher)).isEmpty();
        assertThat(CypherParsingSupport.extractPropertyReferences(cypher)).isEmpty();
    }
}
