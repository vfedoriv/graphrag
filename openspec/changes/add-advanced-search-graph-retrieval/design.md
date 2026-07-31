## Context

The graph already stores canonical facts and `GraphExtractionEvidence` linked from persisted extraction-parent chunks through `HAS_GRAPH_EVIDENCE` and `ASSERTS_*`. The generic ask path generates Cypher text, while advanced search requires a smaller, validated retrieval language.

This is proposal 2 of 7 and can be implemented independently of text retrieval. Its output is normalized with other branches by proposal 3.

## Goals / Non-Goals

**Goals:**

- Express common entity, relationship, projection, ordering, and aggregation requests without arbitrary Cypher.
- Enforce schema, KB, row, and hop bounds before execution.
- Preserve authoritative graph-fact provenance and parent citations.

**Non-Goals:**

- Replace generic ask Cypher generation or validation.
- Support variable-length traversal, writes, procedures, or arbitrary expressions.
- Infer child citations for graph facts.

## Decisions

### Compile a closed typed IR

The IR uses enums/sealed variants for allowed filters and aggregations and schema identifiers for labels, relationship types, and properties. Literals are always parameters. Unsupported constructs are validation failures, not pass-through text.

Alternative: sanitize model-generated Cypher. Rejected because textual validation cannot provide the same closed-world guarantee.

### Inject scope through evidence

Every query anchors canonical facts to `GraphExtractionEvidence.knowledgeBaseId`. Returned node and relationship facts must traverse evidence back to its `sourceChunkId`, validate the parent belongs to the same KB/document/run, and expose that parent as the graph citation.

### Bound shape before rendering

Validation permits at most two typed hops, configured row limits, known projections, ordering, and a small aggregation allowlist. The renderer owns query templates and returns executable Cypher only internally.

## Risks / Trade-offs

- [The IR is initially less expressive than Cypher] → Add constructs only with explicit schema validation and fixtures.
- [Legacy evidence is incomplete] → Exclude unsupported facts with a diagnostic rather than fabricate provenance.
- [Scope injection is accidentally bypassed] → Centralize rendering and integration-test adversarial cross-KB fixtures.

## Migration Plan

1. Add IR and pure validation tests.
2. Add parameterized renderer and execution adapter.
3. Add evidence-to-parent resolution and graph fact DTOs.
4. Keep the branch internal until fusion orchestration is available.

## Open Questions

None.
