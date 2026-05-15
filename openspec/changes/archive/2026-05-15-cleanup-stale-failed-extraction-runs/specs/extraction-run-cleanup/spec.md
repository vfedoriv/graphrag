## ADDED Requirements

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
The system SHALL delete nodes that become orphaned (no remaining relationships) as a direct result of failed-run cleanup.

#### Scenario: Node linked only through failed run
- **WHEN** a node has relationships only to failed run nodes that are deleted
- **THEN** that node is deleted after failed-run cleanup

#### Scenario: Node shared with completed run
- **WHEN** a node is linked to both a deleted failed run and a retained completed run
- **THEN** that node is not deleted

### Requirement: Cleanup executes only after successful run persistence
The system MUST execute failed-run cleanup only when the current extraction run has been persisted as successful.

#### Scenario: Current run fails
- **WHEN** the current extraction run ends in failure
- **THEN** failed-run cleanup is not executed
