# advanced-search-answering Specification

## Purpose
TBD - define evidence-grounded advanced search answers with validated citations.

## Requirements

### Requirement: Advanced search returns a structured answer
The system SHALL persist and return answer text, confidence, limitations, typed substantive claims, ranked evidence, supporting graph facts, and sanitized diagnostics for completed or partial runs.

#### Scenario: Sufficient evidence is available
- **WHEN** synthesis produces a valid evidence-grounded result
- **THEN** the run becomes `COMPLETED` and its public result contains the validated structured answer

### Requirement: Substantive claims have referentially valid citations
Every substantive claim SHALL reference one or more citation IDs present in the final evidence catalog, and graph claims SHALL additionally identify known graph fact and extraction evidence IDs.

#### Scenario: Text claim cites a precise child
- **WHEN** a claim is supported by text retrieval
- **THEN** its citation resolves to the page-bounded retrieval child rather than synthesis-only parent context

#### Scenario: Graph claim cites a fact
- **WHEN** a claim depends on a graph fact
- **THEN** its citation resolves to the persisted extraction parent and the result identifies the supporting fact/evidence IDs

### Requirement: Retrieved content is untrusted prompt data
The system SHALL delimit retrieved excerpts from system instructions and SHALL ignore instructions contained within document evidence.

#### Scenario: Evidence contains an instruction to omit citations
- **WHEN** synthesis receives that excerpt
- **THEN** citation and output rules remain authoritative and the embedded instruction is treated only as document data

### Requirement: Invalid synthesis is repaired at most once
The system SHALL validate the complete structured answer and MAY make one bounded repair call when time remains; it SHALL NOT publish an invalid answer after repair fails.

#### Scenario: Citation references an unknown ID
- **WHEN** initial synthesis contains an unknown citation and repair succeeds
- **THEN** only the repaired validated result is persisted

#### Scenario: Repair remains invalid
- **WHEN** the single repair attempt fails validation
- **THEN** the run becomes `PARTIAL` with retained evidence and an explicit answer-unavailable outcome

### Requirement: Insufficient evidence produces explicit abstention
The system SHALL preserve evidence and limitations and SHALL avoid unsupported substantive claims when sufficiency or citation support is inadequate.

#### Scenario: No usable evidence remains
- **WHEN** retrieval and validation produce no claim-supporting evidence
- **THEN** the result reports insufficient evidence rather than fabricating an answer

### Requirement: Versioned advanced-search results have an explicit public schema
The version-1 result API SHALL expose typed answer, confidence, limitation, claim, source-range, evidence, context, graph-fact, answer-diagnostic, and retrieval/ranking diagnostic structures while retaining `payloadVersion` as the payload evolution boundary.

#### Scenario: Completed result is documented
- **WHEN** generated OpenAPI describes a completed version-1 result
- **THEN** clients can discover every version-1 property, enum, collection item, and nullable field without interpreting an untyped JSON object

#### Scenario: Partial or abstaining result is returned
- **WHEN** a run is partial, has insufficient evidence, or cannot publish a validated answer
- **THEN** the same typed schema represents available evidence, limitations, diagnostics, and nullable optional values

#### Scenario: Unsupported stored payload version is read
- **WHEN** a result has a payload version not supported by the deployed API
- **THEN** the system fails explicitly instead of coercing the payload into the version-1 DTO

### Requirement: Citation evidence snapshots human-readable source metadata
Every newly persisted evidence and context entry SHALL snapshot its source document original filename, content type, and stable display label as immutable run-result citation metadata.

#### Scenario: Source document is later replaced or renamed
- **WHEN** a historical result is read after current document metadata has changed
- **THEN** its evidence retains the source filename, content type, and display label captured when the result was created

#### Scenario: Source document is later unavailable
- **WHEN** current document metadata cannot be resolved while reading a historical result
- **THEN** the persisted citation metadata remains available without joining the current document list

#### Scenario: Legacy result lacks source metadata
- **WHEN** a version-1 result persisted before this change is read
- **THEN** it remains readable with explicitly nullable source metadata fields

### Requirement: Result fixtures cover supported terminal answer shapes
Serialization and OpenAPI contract tests SHALL cover completed, partial, insufficient-evidence, and answer-unavailable version-1 results and SHALL validate citation and graph-fact referential integrity.

#### Scenario: Contract fixture is changed incompatibly
- **WHEN** a DTO or serializer change removes or changes a required version-1 property without changing the payload version
- **THEN** the contract test fails before publication
