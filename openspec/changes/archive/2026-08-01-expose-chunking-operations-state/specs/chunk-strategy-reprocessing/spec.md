## ADDED Requirements

### Requirement: Chunk migration creation is bound to previewable target state
Chunk migration creation SHALL recompute the same target and selection policy exposed by preview and SHALL require the client-supplied `expectedChunkerRevision` to match the current effective target.

#### Scenario: Previewed target remains current
- **WHEN** a client creates a plan with the revision returned by preview and all blockers remain clear
- **THEN** the durable plan snapshots the recomputed target and selected documents

#### Scenario: Readiness changes after preview
- **WHEN** a blocker appears before plan creation
- **THEN** creation returns `409 Conflict` and creates no plan or items

### Requirement: Retry policy uses a closed mode
The retry API SHALL accept the closed mode `RESNAPSHOT_UNRESOLVED`, preserve prior successful items, and resnapshot only unresolved documents under the then-current target.

#### Scenario: Explicit retry mode is submitted
- **WHEN** an eligible terminal plan is retried with `mode=RESNAPSHOT_UNRESOLVED`
- **THEN** a linked plan is created for the newly snapshotted unresolved targets only

#### Scenario: Unsupported retry mode is submitted
- **WHEN** the request contains an unknown retry mode
- **THEN** the system returns `400 Bad Request` and creates no plan

#### Scenario: Deprecated valid boolean is submitted during compatibility
- **WHEN** a legacy client submits `resnapshotUnresolvedDocuments=true` without a mode during the compatibility window
- **THEN** the system treats it as `RESNAPSHOT_UNRESOLVED` and marks the boolean deprecated in OpenAPI

#### Scenario: Unsupported false boolean is submitted
- **WHEN** a legacy client submits `resnapshotUnresolvedDocuments=false`
- **THEN** the system returns `400 Bad Request` without implying that retry without resnapshot is supported
