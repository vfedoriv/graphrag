# extraction-run-cleanup Specification

## Purpose
Define cleanup behavior for extraction runs so stale runs and artifacts are removed safely after successful processing.
## Requirements
### Requirement: Failed extraction runs are cleaned after a successful run
The system SHALL delete prior failed extraction run nodes for the same document after a new extraction run completes successfully.

#### Scenario: Successful retry after failure
- **WHEN** a document has at least one existing failed extraction run and a later extraction run for that document finishes with `COMPLETED` status
- **THEN** prior failed extraction run nodes for that document are deleted

### Requirement: Relationships of deleted failed runs are removed
The system SHALL remove relationships attached to failed extraction run nodes that are deleted during cleanup.

#### Scenario: Failed run has linked entities
- **WHEN** failed extraction run nodes are deleted
- **THEN** all relationships attached to those failed run nodes are removed as part of the same cleanup operation

### Requirement: Orphaned nodes created by cleanup are removed
The system SHALL delete extracted nodes for the same document that become obsolete as a direct result of extraction-run cleanup, including nodes that remain connected only to stale extracted graph data and no longer have retained `CREATED_NODE` provenance from an existing extraction run.

#### Scenario: Node linked only through deleted run
- **WHEN** a node was created only by extraction run nodes that are deleted during cleanup
- **THEN** that node is deleted after cleanup

#### Scenario: Node shared with retained run
- **WHEN** a node is linked to both a deleted run and a retained run
- **THEN** that node is not deleted

#### Scenario: Stale connected subgraph
- **WHEN** extracted nodes for the same document no longer have `CREATED_NODE` provenance from any retained extraction run but still have relationships to each other
- **THEN** those extracted nodes are deleted after cleanup

### Requirement: Cleanup executes only after successful run persistence
The system MUST execute extraction-run cleanup only when the current extraction run has been persisted as successful.

#### Scenario: Current run fails
- **WHEN** the current extraction run ends in failure
- **THEN** extraction-run cleanup is not executed

#### Scenario: Current run succeeds
- **WHEN** the current extraction run is persisted with `COMPLETED` status
- **THEN** extraction-run cleanup is executed according to configured cleanup rules

### Requirement: Overwrite requires explicit confirmation when completed run exists
The system SHALL reject starting a new extraction for a document that already has a `COMPLETED` extraction run unless the request explicitly sets `allowOverwrite=true`.

#### Scenario: Completed run exists and overwrite not allowed
- **WHEN** a document already has at least one `COMPLETED` extraction run and a new extraction request is sent with `allowOverwrite=false` or omitted
- **THEN** the system rejects the request and does not start a new extraction run

#### Scenario: Completed run exists and overwrite allowed
- **WHEN** a document already has at least one `COMPLETED` extraction run and a new extraction request is sent with `allowOverwrite=true`
- **THEN** the system starts a new extraction run

### Requirement: Prior completed runs are removed after successful overwrite run
The system SHALL delete prior `COMPLETED` extraction run nodes for the same document after a newer extraction run completes successfully and was started with overwrite approval.

#### Scenario: Successful overwrite run
- **WHEN** a document has an existing `COMPLETED` extraction run, a new run is started with `allowOverwrite=true`, and the new run reaches `COMPLETED`
- **THEN** prior completed extraction run nodes for that document are deleted while the newest completed run is retained

#### Scenario: Overwrite run fails
- **WHEN** a document has an existing `COMPLETED` extraction run, a new run is started with `allowOverwrite=true`, and the new run fails
- **THEN** prior completed extraction run nodes are not deleted

### Requirement: Relationships of deleted completed runs are removed
The system SHALL remove relationships attached to completed extraction run nodes that are deleted during overwrite cleanup.

#### Scenario: Deleted completed run has linked nodes
- **WHEN** completed extraction run nodes are deleted after successful overwrite
- **THEN** all relationships attached to those deleted run nodes are removed as part of the same cleanup operation

### Requirement: Graph relationships from deleted runs are removed
The system SHALL remove extraction evidence for graph relationships produced by extraction runs deleted during cleanup and SHALL retain a canonical graph relationship when retained evidence from another run or document still supports it.

#### Scenario: Deleted run has domain relationship
- **WHEN** an extraction run is deleted during cleanup and it owns evidence for a graph relationship
- **THEN** that extraction evidence is deleted as part of cleanup

#### Scenario: Retained run has domain relationship
- **WHEN** a canonical graph relationship has evidence for the retained current extraction run
- **THEN** cleanup does not delete the canonical relationship because of stale-run cleanup

#### Scenario: Another document has domain relationship evidence
- **WHEN** a stale run is deleted and another document retains evidence for the same canonical graph relationship
- **THEN** cleanup removes the stale run's evidence
- **AND** the canonical relationship and the other document's evidence remain

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

### Requirement: Extraction cleanup uses relational run authority
The system SHALL resolve failed, stale, replaced, and current extraction run identifiers from PostgreSQL and SHALL remove graph evidence and artifacts by stable copied identifiers.

#### Scenario: A failed retry left partial evidence
- **WHEN** recovery identifies a failed extraction run
- **THEN** graph cleanup removes evidence scoped to that run ID
- **AND** evidence and facts belonging only to unrelated runs remain

#### Scenario: A completed extraction is overwritten
- **WHEN** the replacement run completes successfully
- **THEN** prior completed run state and graph evidence are retired according to existing overwrite semantics
