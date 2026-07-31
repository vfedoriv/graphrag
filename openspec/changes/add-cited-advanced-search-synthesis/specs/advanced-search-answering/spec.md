## ADDED Requirements

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
