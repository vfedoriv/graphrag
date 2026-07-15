## ADDED Requirements

### Requirement: Candidate retrieval has an explicit reviewed page contract
The system SHALL return draft candidates in a typed page envelope containing zero-based page number, bounded page size, total element count, and typed candidate content ordered deterministically.

#### Scenario: Client reads a candidate page
- **WHEN** a client requests a valid candidate page for an owned draft with a current aggregate
- **THEN** the response contains `page`, `size`, `totalElements`, and `content`
- **AND** each content item explicitly describes the discovery candidate fields, recommendation state, and evidence

#### Scenario: Candidate has a persisted decision
- **WHEN** a candidate has one or more append-only review decisions
- **THEN** its response contains the effective persistent review state and latest decision identifier
- **AND** the analyzer recommendation remains a separately named field

#### Scenario: Candidate was rejected
- **WHEN** the latest decision rejects an evidence-backed candidate
- **THEN** candidate retrieval still returns the candidate and its evidence
- **AND** its effective persistent review state is `REJECTED`
