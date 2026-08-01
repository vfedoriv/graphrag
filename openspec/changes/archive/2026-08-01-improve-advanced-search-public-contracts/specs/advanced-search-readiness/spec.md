## ADDED Requirements

### Requirement: Advanced-search readiness is inspectable
The system SHALL expose an owned knowledge-base advanced-search readiness resource that reports whether a run can be admitted, the evaluated AI profile identity and revision, graph-branch availability, embedded-corpus presence, and stable blocker or informational codes without making an external provider request.

#### Scenario: Search is fully ready
- **WHEN** the active profile has structurally usable chat and embedding configuration and stored embeddings are compatible
- **THEN** readiness reports `ready=true` with no blocking entries

#### Scenario: Knowledge base ownership fails
- **WHEN** readiness is requested for a missing or inaccessible knowledge base
- **THEN** the system returns the established ownership-safe `404` response

### Requirement: Readiness distinguishes blockers from degraded capabilities
The readiness policy SHALL block unusable provider configuration and incompatible stored embedding state, SHALL treat an absent active schema as text-only search, and SHALL permit an empty corpus to produce an insufficient-evidence result.

#### Scenario: Active schema is absent
- **WHEN** provider readiness succeeds but the knowledge base has no active schema
- **THEN** readiness remains successful and reports that the graph branch is unavailable

#### Scenario: Corpus has no embedded chunks
- **WHEN** provider readiness succeeds but the knowledge base contains no embedded chunks
- **THEN** readiness remains successful and identifies the empty corpus as informational

#### Scenario: Embedding space is incompatible
- **WHEN** stored embedded chunks are incompatible with the active profile embedding space
- **THEN** readiness reports `ready=false` with a stable embedding-incompatibility blocker code

### Requirement: Run admission enforces the reported readiness policy
Run creation SHALL evaluate the same readiness policy before reserving bounded executor capacity or persisting a run and SHALL reject blockers with RFC 7807 `409 Conflict`, a stable problem type, and machine-readable blocker codes.

#### Scenario: Blocking readiness failure
- **WHEN** a create request targets a knowledge base whose readiness result contains a blocking provider or embedding condition
- **THEN** no queue capacity is retained, no run row is created, and the response identifies the same blocker code as the readiness resource

#### Scenario: Empty corpus admission
- **WHEN** a create request targets a ready knowledge base with no usable evidence corpus
- **THEN** the run is admitted and may complete with an explicit insufficient-evidence answer

### Requirement: Readiness remains privacy safe
Readiness responses and operational logs SHALL NOT expose AI profile API keys, submitted queries, document text, model responses, or evidence content.

#### Scenario: Readiness blocker is logged
- **WHEN** admission rejects a provider readiness blocker
- **THEN** normal logs contain only safe identifiers and blocker codes and contain no secret or user content
