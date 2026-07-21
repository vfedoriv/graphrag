## MODIFIED Requirements

### Requirement: Draft evaluation uses explicitly held-out documents
The system SHALL allow a client to start a durable evaluation of the current reviewed draft projection against selected documents owned by the draft knowledge base whose content did not contribute active evidence to that projection. Its canonical decision snapshot SHALL support non-empty decision history and preserve each decision timestamp as an ISO-8601 string.

#### Scenario: Start a held-out evaluation
- **WHEN** a client selects eligible knowledge-base documents and starts evaluation for a current draft revision
- **THEN** the system snapshots the draft projection, decisions, document SHA-256 values, AI profile identifier and revision, prompt revision, and evaluation settings
- **AND** returns an accepted response with a durable evaluation run identifier and status location

#### Scenario: Start an evaluation with decision history
- **WHEN** a current draft has one or more recorded decisions and a client starts evaluation with eligible held-out documents
- **THEN** the system creates the durable evaluation run without a decision-serialization failure
- **AND** the run's canonical decision snapshot contains every decision and represents each `createdAt` value as an ISO-8601 string

#### Scenario: Discovery source is selected as held-out
- **WHEN** a selected document's SHA-256 matches a successful source result in the analysis run that produced the current aggregate
- **THEN** the system rejects it as ineligible with reason `ACTIVE_DISCOVERY_EVIDENCE`
- **AND** it does not start model calls

#### Scenario: Draft-owned discovery content is uploaded again as a knowledge-base document
- **WHEN** a selected knowledge-base document's SHA-256 matches content that successfully contributed through a draft-owned `FILE` or `TEXT` source
- **THEN** the system rejects it as active discovery evidence even though the source type and resource identifier differ
- **AND** it does not start model calls

#### Scenario: Held-out document changes before evaluation
- **WHEN** a selected document's current SHA-256 differs from the evaluation snapshot before it is processed
- **THEN** that document receives a stale-source outcome
- **AND** replacement content is not evaluated under the prior snapshot

#### Scenario: Evaluation does not persist extracted graph data
- **WHEN** a held-out document is dry-extracted against the draft projection
- **THEN** the system does not persist document chunks, embeddings, extraction runs, nodes, or relationships in the knowledge-base graph

#### Scenario: Knowledge-base document is prepared for held-out evaluation
- **WHEN** a client uploads or processes a normal knowledge-base document without adding it as a draft source
- **THEN** the operation does not advance a schema draft revision or clear its current aggregate
- **AND** the document remains eligible when its content did not contribute to the current aggregate
