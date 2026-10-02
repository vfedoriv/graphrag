package io.github.vfedoriv.graphrag.schemas.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vfedoriv.graphrag.dto.SchemaDiscoveryRequest;
import org.springframework.stereotype.Component;

@Component
public final class CandidateExtractionPromptFactory {

    private final ObjectMapper objectMapper;

    public CandidateExtractionPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String prompt(
        PreparedDiscoverySource source,
        PreparedDiscoverySource.AnalysisChunk chunk,
        SchemaDiscoveryRequest request,
        String formatInstructions
    ) {
        return """
            You extract evidence-backed graph schema candidates from exactly one source chunk.
            Contract revision: %s

            Rules:
            - Return only the requested structured object. Never return a complete schema.
            - Propose nodes, node properties, node keys, directed relationship triples, and relationship properties.
            - Use STRING, INTEGER, LONG, FLOAT, DOUBLE, BOOLEAN, DATE, DATETIME, LOCAL_DATETIME, or LIST_STRING property types.
            - A node key must reference properties proposed for that node. Prefer stable domain identifiers; do not invent UUID keys.
            - Relationship endpoints must be proposed nodes or explicit guidance concepts.
            - origin is OBSERVED only for direct chunk evidence; use INFERRED for semantic inference.
            - Do not include source text excerpts or previews in any field.
            - Alias suggestions are review-only; do not merge differently named concepts.

            Source coordinates: sourceId=%s, chunkId=%s, sourceType=%s
            Structured guidance JSON: %s
            Additional instructions: %s

            Source chunk:
            %s

            %s
            """.formatted(
            DiscoveryContracts.PROMPT_CONTRACT_REVISION,
            source.sourceId(),
            chunk.id(),
            source.type(),
            json(request.guidance()),
            request.additionalInstructions() == null ? "" : request.additionalInstructions(),
            chunk.text(),
            formatInstructions == null ? "" : formatInstructions
        );
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Discovery guidance cannot be serialized", exception);
        }
    }
}
