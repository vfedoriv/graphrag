## MODIFIED Requirements

### Requirement: Reprocessing plan history is discoverable by knowledge base and draft
The system SHALL provide a paginated knowledge-base reprocessing-plan history that can be filtered by an owned draft and by optional `reason`, `selection`, and `status` values whose summaries include identifiers, statuses, document counts, timestamps, retryability, retry lineage, latest status, and current target validity.

#### Scenario: Client recovers a plan after losing the identifier
- **WHEN** a client lists reprocessing plans for a knowledge base with any valid combination of filters
- **THEN** the system applies all filters before pagination and total calculation and returns a bounded page ordered by creation time descending with deterministic identifier ties
- **AND** each summary contains enough state to resume polling or open the detailed plan resource

#### Scenario: Chunk migration history is requested
- **WHEN** a client filters by `reason=CHUNK_STRATEGY_MIGRATION`
- **THEN** every returned plan is a chunk migration and `totalElements` counts only matching plans

#### Scenario: Multiple plan filters are combined
- **WHEN** `reason`, `selection`, and `status` are supplied together
- **THEN** only plans matching every supplied filter are returned and sparse client-side post-filtering is unnecessary

#### Scenario: Existing unfiltered behavior is used
- **WHEN** no optional filter is supplied
- **THEN** the system returns the existing complete knowledge-base history with unchanged deterministic ordering

#### Scenario: Latest plan is identified
- **WHEN** more than one plan exists for a draft
- **THEN** only the most recently created plan is marked latest
- **AND** older plans and retry lineage remain visible in history

#### Scenario: Plan target is no longer active
- **WHEN** a plan's target schema, snapshotted content hash, profile, embedding space, or chunker revision no longer matches its applicable current target
- **THEN** the summary reports that the target is not current
- **AND** retryability reflects whether the retry command would be accepted under current target state

#### Scenario: Foreign draft filter is supplied
- **WHEN** a client filters plan history with a draft not owned by the knowledge base
- **THEN** the system rejects the request using established ownership-safe not-found behavior
