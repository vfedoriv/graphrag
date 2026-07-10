# knowledge-base-lifecycle Specification

## Purpose
Define consistent knowledge-base provisioning, deletion, and legacy ownership repair.

## Requirements
### Requirement: Knowledge-base provisioning is consistent
The system SHALL use one idempotent lifecycle path to provision a knowledge base for every supported creation mechanism.

#### Scenario: Client creates a knowledge base
- **WHEN** a client creates a knowledge base with a new identifier
- **THEN** the system persists the knowledge base with its required metadata and the default AI profile assignment

#### Scenario: Schema activation provisions a missing knowledge base
- **WHEN** schema activation targets a knowledge base identifier that does not yet exist
- **THEN** the system provisions that knowledge base through the common lifecycle path before activation
- **AND** the provisioned knowledge base has the default AI profile assignment

### Requirement: Knowledge-base deletion protects owned documents
The system MUST reject deletion of a knowledge base that owns one or more document records.

#### Scenario: Delete non-empty knowledge base
- **WHEN** a client deletes a knowledge base with owned documents
- **THEN** the system returns a conflict that identifies the remaining document count
- **AND** the knowledge base, document records, and stored binaries remain unchanged

#### Scenario: Delete empty knowledge base
- **WHEN** a client deletes a knowledge base with no owned document records
- **THEN** the system removes the knowledge base and its schema associations

### Requirement: Legacy document ownership is repaired
The system SHALL repair a legacy document whose knowledge-base ID has no corresponding knowledge-base record before enforcing ownership validation.

#### Scenario: Legacy document references missing knowledge base
- **WHEN** lifecycle migration finds a document with a missing knowledge-base record
- **THEN** the system provisions the missing knowledge base through the common lifecycle path
- **AND** preserves the existing document record and binary reference
