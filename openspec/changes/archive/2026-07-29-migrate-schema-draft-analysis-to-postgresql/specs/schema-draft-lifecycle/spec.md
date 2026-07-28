## ADDED Requirements

### Requirement: Draft identity and lifecycle are relational
The system SHALL persist draft ownership, status, current revision references, running analysis claim, timestamps, and optimistic version in PostgreSQL.

#### Scenario: A draft is created
- **WHEN** a valid knowledge-base-scoped draft is requested
- **THEN** its assigned ID and ownership commit relationally
- **AND** the existing API representation is preserved

#### Scenario: A stale draft mutation occurs
- **WHEN** a caller mutates a draft using stale state
- **THEN** optimistic concurrency rejects the mutation

### Requirement: Draft navigation remains bounded during staged migration
The system SHALL produce ownership-safe draft summaries and detail navigation through bounded relational projections and SHALL represent not-yet-migrated downstream workflow summaries consistently.

#### Scenario: Drafts are listed
- **WHEN** a knowledge base contains drafts with analysis and review history
- **THEN** their current revisions and summary counts are loaded without per-draft repository queries
