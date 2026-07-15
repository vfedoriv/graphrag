## ADDED Requirements

### Requirement: Reprocessing items use the standard page envelope
The system SHALL represent the selected per-document item slice in a reprocessing plan response as a typed page envelope with zero-based page number, bounded page size, total element count, and deterministic content order.

#### Scenario: Client polls a paged reprocessing plan
- **WHEN** a client requests a reprocessing plan with page and size parameters
- **THEN** the response includes the bounded item page as `page`, `size`, `totalElements`, and `content`
- **AND** aggregate queued, running, succeeded, failed, stale, and blocked counts describe the entire plan

#### Scenario: Plan contains no items on the selected page
- **WHEN** the selected page has no matching plan items
- **THEN** the response returns empty content while preserving the requested page metadata and total element count
