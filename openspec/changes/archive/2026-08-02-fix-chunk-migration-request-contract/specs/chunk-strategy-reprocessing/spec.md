## MODIFIED Requirements

### Requirement: Typed chunk migration plan creation
The knowledge-base reprocessing resource SHALL accept `CHUNK_STRATEGY_MIGRATION` with `OUTDATED_STRATEGY`, non-empty owned `DOCUMENT_IDS`, or forced `ALL` selection and SHALL require `expectedChunkerRevision`. The request SHALL be accepted when the schema-specific `allDocuments` property is omitted or `null`, and migration document selection SHALL be determined only by `selection` and its reason-specific inputs.

#### Scenario: Outdated selection
- **WHEN** a valid request selects `OUTDATED_STRATEGY`
- **THEN** the plan includes owned documents with no chunks or a persisted effective revision different from the requested current revision

#### Scenario: Migration omits schema selection property
- **WHEN** a client submits a valid `CHUNK_STRATEGY_MIGRATION` request with `selection` and `expectedChunkerRevision` but omits `allDocuments`
- **THEN** the request reaches reason-specific migration validation and plan creation without a JSON deserialization error

#### Scenario: Migration explicitly nulls schema selection property
- **WHEN** a client submits an otherwise valid `CHUNK_STRATEGY_MIGRATION` request with `allDocuments` set to `null`
- **THEN** migration selection remains determined by `selection`

#### Scenario: Legacy migration supplies schema selection property
- **WHEN** a migration client supplies `allDocuments` as `false` or `true` alongside a valid migration `selection`
- **THEN** the schema-specific property does not change the migration document selection

#### Scenario: Stale expected revision
- **WHEN** `expectedChunkerRevision` differs from the effective server revision
- **THEN** plan creation returns RFC 7807 `409 Conflict` and queues no items
