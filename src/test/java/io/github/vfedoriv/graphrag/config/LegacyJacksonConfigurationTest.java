package io.github.vfedoriv.graphrag.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftDecisionType;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftReviewState;
import io.github.vfedoriv.graphrag.schemas.drafts.api.model.SchemaDraftDtos.DecisionResponse;
import io.github.vfedoriv.graphrag.schemas.drafts.application.SchemaDraftJsonSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyJacksonConfigurationTest {

    @Test
    void canonicalizesDecisionTimestampsAsIso8601Strings() throws Exception {
        ObjectMapper objectMapper = new LegacyJacksonConfiguration().legacyObjectMapper();
        SchemaDraftJsonSupport jsonSupport = new SchemaDraftJsonSupport(objectMapper);
        Instant createdAt = Instant.parse("2026-07-21T10:15:30.123456Z");
        DecisionResponse decision = new DecisionResponse("decision-1", 1, 2, SchemaDraftDecisionType.REJECT,
            SchemaDraftReviewState.REJECTED, "node-property:Person:displayName", null, null,
            "not part of the domain", createdAt);

        String canonical = jsonSupport.canonical(List.of(decision));
        JsonNode decisions = objectMapper.readTree(canonical);

        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).path("createdAt").isTextual()).isTrue();
        assertThat(decisions.get(0).path("createdAt").asText()).isEqualTo(createdAt.toString());
    }
}
