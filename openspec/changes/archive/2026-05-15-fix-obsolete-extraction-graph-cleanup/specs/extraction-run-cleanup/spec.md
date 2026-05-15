## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Graph relationships from deleted runs are removed
The system SHALL remove extracted graph relationships for the same document when those relationships were produced by extraction runs deleted during cleanup.

#### Scenario: Deleted run has domain relationship
- **WHEN** an extraction run is deleted during cleanup and an extracted graph relationship has the same document id and deleted run id as provenance
- **THEN** that extracted graph relationship is deleted as part of cleanup

#### Scenario: Retained run has domain relationship
- **WHEN** an extracted graph relationship has provenance for the retained current extraction run
- **THEN** that extracted graph relationship is not deleted by cleanup
