# graph-extraction-result-contract Specification

## Purpose
TBD - created by archiving change harden-extraction-cleanup-and-schema-validation. Update Purpose after archive.
## Requirements
### Requirement: Extraction result collections are never null
The system SHALL normalize graph extraction result node and relationship collections to empty collections when an extraction client supplies null collections.

#### Scenario: Extraction client returns null node list
- **WHEN** a graph extraction result is created with a null node collection
- **THEN** the result exposes an empty node collection to validation and graph persistence

#### Scenario: Extraction client returns null relationship list
- **WHEN** a graph extraction result is created with a null relationship collection
- **THEN** the result exposes an empty relationship collection to validation and graph persistence

#### Scenario: Extraction client returns populated collections
- **WHEN** a graph extraction result is created with populated node or relationship collections
- **THEN** the result exposes those collections without dropping entries

