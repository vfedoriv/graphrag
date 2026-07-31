## ADDED Requirements

### Requirement: Advanced search is submitted as a durable run
The system SHALL accept `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs` with a bounded query and evidence options and SHALL return `202 Accepted` with the owned run identity, status, stage, timestamps, and polling routes.

#### Scenario: Admission capacity is available
- **WHEN** a valid request is submitted for an existing knowledge base
- **THEN** a durable `QUEUED` run is created with immutable effective snapshots

#### Scenario: Admission capacity is exhausted
- **WHEN** the bounded run executor and queue have no capacity
- **THEN** the system returns `429` before creating a run row

### Requirement: Run history and state are knowledge-base scoped
The system SHALL expose newest-first paginated history with optional status filtering and an owned run status resource containing stage counters, cancellation state, timestamps, and sanitized failures.

#### Scenario: Run belongs to another knowledge base
- **WHEN** a client requests its status through a different knowledge base path
- **THEN** the system returns `404` without disclosing the run

### Requirement: Result availability follows lifecycle state
The system SHALL return completed or partial validated results from the result route, `409` while no result is available, and `404` after ownership failure or retention cleanup.

#### Scenario: Run is still executing
- **WHEN** the result route is requested before a result is persisted
- **THEN** RFC 7807 `409 Conflict` identifies the current status and stage

### Requirement: Cancellation is cooperative and idempotent
The system SHALL immediately cancel queued work, request cancellation of running branch futures, check cancellation between stages, and ignore late results after cancellation.

#### Scenario: Cancellation is repeated
- **WHEN** an already cancelled run is cancelled again
- **THEN** the same terminal cancellation state is returned without new work

### Requirement: Run state is durable and recoverable
The system SHALL persist optimistic state transitions and atomic worker claims in PostgreSQL and SHALL mark stale queued or running rows `INTERRUPTED` at startup without replaying them.

#### Scenario: Application restarts during retrieval
- **WHEN** startup recovery finds a stale running row
- **THEN** the row becomes `INTERRUPTED` and no model or retriever call is automatically repeated

### Requirement: Terminal artifacts expire predictably
The system SHALL delete terminal run, attempt, and result artifacts in bounded batches after the configured expiry and SHALL cascade them when their knowledge base is deleted.

#### Scenario: Retention period elapses
- **WHEN** scheduled cleanup selects an expired terminal run
- **THEN** its attempts and result are removed atomically with the run
