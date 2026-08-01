## ADDED Requirements

### Requirement: Effective chunking state has one authoritative resource
The system SHALL expose a read-only chunking-state resource containing canonical effective chunking values and sources, strategy/tokenizer/parser/representation revisions, settings hash, effective chunker revision, migration lifecycle, and compatibility aliases with their precedence.

#### Scenario: Client opens chunking settings
- **WHEN** the client reads the chunking-state resource
- **THEN** it can render the effective strategy and revision without deriving them from repeated generic runtime-setting rows

#### Scenario: Canonical key overrides a compatibility alias
- **WHEN** both a canonical chunking setting and its compatibility alias are configured
- **THEN** the resource reports the canonical effective value and identifies the alias as non-authoritative

### Requirement: Chunk migration selection can be previewed without mutation
The system SHALL expose a knowledge-base-scoped read-only preview operation accepting `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, or `ALL`, optional valid processing options, and bounded paging without creating operational state.

#### Scenario: Outdated migration is previewed
- **WHEN** a client previews `OUTDATED_STRATEGY`
- **THEN** the response reports whole-knowledge-base counts for no-chunk, outdated, and current documents plus the selected count and a deterministic selected-document page

#### Scenario: Explicit documents are previewed
- **WHEN** a client previews distinct owned `DOCUMENT_IDS`
- **THEN** only those documents are selected and their document-specific effective targets are evaluated

#### Scenario: Preview completes
- **WHEN** any valid preview response is returned
- **THEN** no plan, item, processing run, chunk, or graph artifact has been created or changed

### Requirement: Migration preview reports stable readiness blockers
The preview SHALL report the current expected chunker revision, active schema/profile/embedding target, readiness, and stable blocker codes using the same target policy enforced by plan creation.

#### Scenario: Active schema is missing
- **WHEN** a migration preview targets a knowledge base without an active schema
- **THEN** the response reports a blocking active-schema code and creates no work

#### Scenario: Another destructive plan is active
- **WHEN** schema activation or chunk migration already has a queued or running plan for the knowledge base
- **THEN** preview reports the shared destructive-plan blocker

#### Scenario: Previewed target changes before creation
- **WHEN** plan creation submits the previewed `expectedChunkerRevision` after the effective target changes
- **THEN** creation returns RFC 7807 `409 Conflict` and creates no plan

### Requirement: Preview validation preserves ownership safety
Preview selection and processing options SHALL use the same validation and ownership behavior as plan creation.

#### Scenario: Foreign document is selected
- **WHEN** `DOCUMENT_IDS` includes a document outside the requested knowledge base
- **THEN** the system returns the established ownership-safe `404` response

#### Scenario: Selection shape is invalid
- **WHEN** `DOCUMENT_IDS` is empty or a non-document selection includes document IDs
- **THEN** the system returns RFC 7807 `400 Bad Request`
