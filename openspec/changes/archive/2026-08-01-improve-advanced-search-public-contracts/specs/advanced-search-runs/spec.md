## ADDED Requirements

### Requirement: Run history exposes bounded request context
Advanced-search history SHALL return a Unicode-safe bounded query preview plus the snapshotted `maximumEvidence` and `includeEvidenceText` values for every owned run.

#### Scenario: History is reloaded
- **WHEN** a client lists runs after losing local submission state
- **THEN** each summary identifies the question with a bounded preview and reports the applied evidence options

#### Scenario: Query exceeds the preview bound
- **WHEN** a stored query contains more than the configured preview code-point limit
- **THEN** the preview is deterministically truncated without splitting a Unicode code point and the full query is not returned by the list resource

### Requirement: Owned run detail exposes the submitted query
The owned run-detail resource and successful create response SHALL return the complete snapshotted query together with the applied evidence options and existing lifecycle fields.

#### Scenario: Run detail is requested by its owner
- **WHEN** the client retrieves an owned run by identifier
- **THEN** the response contains the full submitted query, `maximumEvidence`, and `includeEvidenceText`

#### Scenario: Run belongs to another knowledge base
- **WHEN** a client requests run detail through a different knowledge-base path
- **THEN** the system returns `404` without disclosing the full query or preview

### Requirement: Request context remains absent from operational logs
Adding query content to owned API responses SHALL NOT add query text or previews to normal application logs.

#### Scenario: Run list and detail are served
- **WHEN** run resources containing request context are returned
- **THEN** operational logs remain limited to identifiers, counts, statuses, timings, and other approved metadata
