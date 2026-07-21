## MODIFIED Requirements

### Requirement: Draft evaluation uses explicitly held-out documents
The system SHALL allow a client to start a durable evaluation of the current reviewed draft projection against selected documents owned by the draft knowledge base whose content did not contribute active evidence to that projection.

#### Scenario: Start a held-out evaluation
- **WHEN** a client selects eligible knowledge-base documents and starts evaluation for a current draft revision
- **THEN** the system snapshots the draft projection, decisions, document SHA-256 values, AI profile identifier and revision, prompt revision, and evaluation settings
- **AND** returns an accepted response with a durable evaluation run identifier and status location

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

### Requirement: Held-out document eligibility is discoverable and authoritative
The system SHALL provide a paginated evaluation-eligible-document view for an owned draft, SHALL expose whether the draft is ready for evaluation, and SHALL use the same content-based eligibility decision when an evaluation is started.

#### Scenario: Current aggregate is unavailable
- **WHEN** the draft has no current aggregate because discovery analysis has not completed or draft inputs changed
- **THEN** the eligibility response reports that evaluation is not ready with reason `DRAFT_ANALYSIS_REQUIRED`
- **AND** documents are not presented as selectable for evaluation
- **AND** evaluation start is rejected before any run or model side effect is created

#### Scenario: Document contributed to the current aggregate
- **WHEN** an owned document's SHA-256 equals the source SHA-256 of a successful result in the analysis run that produced the current aggregate
- **THEN** the eligibility response marks that document ineligible with reason `ACTIVE_DISCOVERY_EVIDENCE`
- **AND** evaluation start rejects selection of that document before model calls begin

#### Scenario: Active document source did not contribute to the current aggregate
- **WHEN** source content is associated with the draft but its current revision did not successfully contribute to the current aggregate
- **THEN** the eligibility response does not exclude a matching knowledge-base document as active discovery evidence

#### Scenario: Document is eligible
- **WHEN** an owned knowledge-base document's SHA-256 does not match any successful source result contributing to the current aggregate
- **THEN** the response marks it eligible with no ineligibility reason

#### Scenario: Duplicate knowledge-base documents contain discovery evidence
- **WHEN** multiple owned knowledge-base documents share a SHA-256 that contributed to the current aggregate
- **THEN** every matching document is marked ineligible regardless of document identifier

#### Scenario: Draft changes between eligibility and start
- **WHEN** the draft revision or current aggregate changes after eligibility is read and before evaluation starts
- **THEN** the established optimistic revision and aggregate checks reject the stale start
- **AND** no evaluation run or model side effect is created
