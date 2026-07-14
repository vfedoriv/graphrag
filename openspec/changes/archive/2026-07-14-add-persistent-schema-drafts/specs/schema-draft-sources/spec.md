## ADDED Requirements

### Requirement: Drafts accept three explicit source types
The system SHALL allow an open draft to contain knowledge-base document references, draft-owned uploaded files, and persisted pasted-text samples, with every source carrying a stable identifier, type, content fingerprint, revision, active state, and timestamps.

#### Scenario: Add an existing knowledge-base document
- **WHEN** a client adds a document owned by the draft knowledge base
- **THEN** the source snapshot records the document identifier and current SHA-256 fingerprint
- **AND** the document binary is not copied into draft storage

#### Scenario: Add a draft file
- **WHEN** a client uploads a supported multipart file to an open draft
- **THEN** the system stores the binary in a draft-owned storage namespace
- **AND** it creates no normal `DocumentUpload` record
- **AND** the source fingerprint is calculated from the stored bytes

#### Scenario: Add a pasted-text sample
- **WHEN** a client adds a non-blank pasted-text sample within configured size limits
- **THEN** the system persists it as draft-owned source content
- **AND** normal logs and list responses do not expose a text preview

#### Scenario: Add duplicate source content
- **WHEN** an active draft source already has the same source type and content fingerprint
- **THEN** the system returns or identifies the existing source instead of creating duplicate active evidence

### Requirement: Document source snapshots detect replacement and deletion
The system SHALL validate a referenced document's ownership and current content fingerprint before analysis and SHALL require an explicit source refresh when the normal document has changed.

#### Scenario: Referenced document is unchanged
- **WHEN** analysis reads a document whose current SHA-256 equals the draft source snapshot
- **THEN** the source is eligible for analysis under that snapshot

#### Scenario: Referenced document was replaced
- **WHEN** the document identifier still exists but its current SHA-256 differs from the draft source snapshot
- **THEN** the system marks the source `STALE`
- **AND** it does not analyze replacement content under the prior source revision

#### Scenario: Client refreshes a stale document source
- **WHEN** a client explicitly refreshes a stale document source using the current draft revision
- **THEN** the source advances to a new revision with the current document fingerprint
- **AND** prior evidence remains linked to the prior source revision for audit

#### Scenario: Referenced document was deleted
- **WHEN** analysis can no longer resolve a referenced document in the knowledge base
- **THEN** the source is `UNAVAILABLE`
- **AND** the source produces a non-retryable analysis outcome until refreshed or removed

### Requirement: Source removal preserves analyzed history
The system SHALL distinguish active source membership from historical source evidence so removing a source cannot rewrite completed analysis history.

#### Scenario: Remove a source that has not been analyzed
- **WHEN** a client removes an active source with no completed analysis result
- **THEN** the system deletes its draft-owned content and metadata
- **AND** referenced normal documents remain unchanged

#### Scenario: Remove a previously analyzed source
- **WHEN** a client removes a source that contributed to a completed analysis
- **THEN** the source becomes inactive for future aggregates
- **AND** historical results and evidence references remain auditable
- **AND** the next aggregate revision excludes its candidates unless separately supported

#### Scenario: Removed source is restored
- **WHEN** a client restores an inactive source whose content remains available and unchanged
- **THEN** the source becomes active at a new draft revision
- **AND** matching completed analysis may be reused under the normal cache-key rules

### Requirement: Draft-owned source storage is reconciled
The system SHALL apply transaction-aware storage mutation tracking and reconciliation to draft-owned files and pasted-text content.

#### Scenario: Metadata persistence fails after source content is stored
- **WHEN** binary storage succeeds but source metadata persistence fails or is interrupted
- **THEN** reconciliation removes or completes the uncommitted draft source artifact
- **AND** unrelated document and draft content remains unchanged

#### Scenario: Open draft deletion cleans source content
- **WHEN** an open draft is successfully deleted
- **THEN** all draft-owned source content is removed
- **AND** storage cleanup failures prevent an incorrectly reported successful deletion

