## ADDED Requirements

### Requirement: Retrieval candidates have one evidence model
The system SHALL normalize retriever results into chunk-keyed evidence candidates while retaining each contribution's retriever, subquery, raw rank, raw score, source bounds, and attached graph facts.

#### Scenario: Two branches return the same child
- **WHEN** dense and lexical retrieval return one chunk
- **THEN** one candidate retains both channel contributions and one precise child citation identity

### Requirement: Fusion uses normalized reciprocal ranks
The system SHALL fuse executed branch rankings with weighted reciprocal-rank fusion using `k=60`, equal default weights, and normalization by each branch's executed subquery count.

#### Scenario: Branches expose incomparable scores
- **WHEN** vector and full-text results are fused
- **THEN** ordering uses ranks rather than direct raw-score comparison

#### Scenario: One branch executes more subqueries
- **WHEN** branch subquery counts differ
- **THEN** normalization prevents the larger branch from gaining weight solely from query count

### Requirement: Expansion preserves evidence identity
The system SHALL expand leading seeds through evidence provenance and validated stored-parent or constrained adjacency context without changing text-child or graph-parent citation identity.

#### Scenario: Valid parent supplies synthesis context
- **WHEN** a retrieved child resolves to a same-scope persisted parent
- **THEN** the parent text is available as bounded context while the text citation remains the child

#### Scenario: Legacy MENTIONS edges exist
- **WHEN** graph expansion runs
- **THEN** `MENTIONS` edges do not contribute advanced-search facts or candidates

### Requirement: Reranking has a deterministic fallback
The system SHALL rerank only the configured bounded pool using structured output from the active profile chat model and SHALL preserve fused order when reranking is unavailable or invalid.

#### Scenario: Reranker output cannot be parsed
- **WHEN** structured relevance validation fails
- **THEN** the final ranking uses deterministic fused order and records fallback use

### Requirement: Final evidence is bounded and diverse
The system SHALL select no more than the requested maximum evidence and SHALL apply the configured per-document cap unless an explicit comparison policy permits broader coverage.

#### Scenario: One document dominates reranked results
- **WHEN** more than the configured per-document number rank highest
- **THEN** lower-ranked evidence from other documents is selected before excess same-document candidates
