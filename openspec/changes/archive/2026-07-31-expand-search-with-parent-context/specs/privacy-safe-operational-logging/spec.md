## ADDED Requirements

### Requirement: Content-free expansion diagnostics
Operational logs SHALL record expansion counts, budget decisions, strategy revisions, validation outcomes, and timings without logging child or parent text, embeddings, prompts, queries, or model responses.

#### Scenario: Parent rejected during expansion
- **WHEN** a candidate parent fails scope validation
- **THEN** logs contain safe identifiers or fingerprints and the rejection reason but no source content
