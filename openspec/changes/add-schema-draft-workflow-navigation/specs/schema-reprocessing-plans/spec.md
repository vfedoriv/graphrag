## ADDED Requirements

### Requirement: Reprocessing plan history is discoverable by knowledge base and draft
The system SHALL provide a paginated knowledge-base reprocessing-plan history that can be filtered by an owned draft and whose summaries include identifiers, statuses, document counts, timestamps, retryability, retry lineage, latest status, and current target-schema validity.

#### Scenario: Client recovers a plan after losing the identifier
- **WHEN** a client lists reprocessing plans for a knowledge base and filters by an owned draft
- **THEN** the system returns a bounded page ordered by creation time descending with deterministic ties
- **AND** each summary contains enough state to resume polling or open the detailed plan resource

#### Scenario: Latest plan is identified
- **WHEN** more than one plan exists for a draft
- **THEN** only the most recently created plan is marked latest
- **AND** older plans and retry lineage remain visible in history

#### Scenario: Plan target is no longer active
- **WHEN** a plan's target schema or snapshotted content hash no longer matches the knowledge base active schema
- **THEN** the summary reports that the target is not current
- **AND** retryability reflects whether the retry command would be accepted under current target state

#### Scenario: Foreign draft filter is supplied
- **WHEN** a client filters plan history with a draft not owned by the knowledge base
- **THEN** the system rejects the request using established ownership-safe not-found behavior
