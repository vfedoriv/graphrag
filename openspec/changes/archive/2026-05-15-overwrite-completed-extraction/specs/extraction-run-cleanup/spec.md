## MODIFIED Requirements

### Requirement: Cleanup executes only after successful run persistence
The system MUST execute extraction-run cleanup only when the current extraction run has been persisted as successful.

#### Scenario: Current run fails
- **WHEN** the current extraction run ends in failure
- **THEN** extraction-run cleanup is not executed

#### Scenario: Current run succeeds
- **WHEN** the current extraction run is persisted with `COMPLETED` status
- **THEN** extraction-run cleanup is executed according to configured cleanup rules

### Requirement: Orphaned nodes created by cleanup are removed
The system SHALL delete nodes that become orphaned (no remaining relationships) as a direct result of extraction-run cleanup.

#### Scenario: Node linked only through deleted run
- **WHEN** a node has relationships only to extraction run nodes that are deleted during cleanup
- **THEN** that node is deleted after cleanup

#### Scenario: Node shared with retained run
- **WHEN** a node is linked to both a deleted run and a retained run
- **THEN** that node is not deleted

## ADDED Requirements

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
