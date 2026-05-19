## ADDED Requirements

### Requirement: Cleanup failures do not alter successful extraction status
The system MUST keep the current extraction run in `COMPLETED` status when extraction and graph persistence have succeeded but post-success cleanup fails.

#### Scenario: Cleanup fails after successful extraction
- **WHEN** a current extraction run has been persisted with `COMPLETED` status and cleanup raises an error
- **THEN** the current extraction run remains in `COMPLETED` status
- **AND** the cleanup failure is logged with the document id and current run id

### Requirement: Cleanup result anomalies are observable
The system SHALL emit a warning when post-success cleanup returns no result row for a persisted completed extraction run.

#### Scenario: Cleanup query returns no row
- **WHEN** cleanup is invoked for a persisted current extraction run with `COMPLETED` status and the cleanup query returns no row
- **THEN** the system logs a warning that cleanup returned no result for the document id and current run id
- **AND** extraction completion logging does not report missing cleanup counters as if they were verified deletion counts

### Requirement: Cleanup counters describe deleted artifacts
The system SHALL expose typed cleanup counters for deleted extraction runs, deleted relationships, and deleted obsolete extracted nodes.

#### Scenario: Cleanup deletes stale run artifacts
- **WHEN** cleanup deletes stale extraction runs, relationships, or obsolete extracted nodes for a document
- **THEN** the cleanup result reports the number of deleted runs
- **AND** the cleanup result reports the number of deleted relationships
- **AND** the cleanup result reports the number of deleted obsolete extracted nodes

### Requirement: Cleanup is scoped to extracted graph artifacts
The system MUST delete obsolete extracted nodes only for the target document's extracted graph data and MUST NOT delete repository infrastructure nodes such as documents, chunks, schemas, knowledge bases, or extraction runs as obsolete extracted nodes.

#### Scenario: Infrastructure node shares source document id
- **WHEN** an infrastructure node has the same `sourceDocumentId` property as the document being cleaned
- **AND** the node is not extracted graph data created by graph extraction
- **THEN** obsolete-node cleanup does not delete that infrastructure node
