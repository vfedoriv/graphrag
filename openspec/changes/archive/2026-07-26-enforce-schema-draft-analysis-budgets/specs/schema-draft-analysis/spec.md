## ADDED Requirements

### Requirement: Draft analysis captures an immutable execution policy
The system SHALL resolve and persist the effective source concurrency, per-source timeout, overall request timeout, and settings fingerprint when a draft analysis run is created, and SHALL use that immutable policy for the entire run.

#### Scenario: Run is created under current settings
- **WHEN** a client starts draft analysis
- **THEN** the run snapshots the current typed discovery execution policy before background work is submitted
- **AND** its status resource exposes the effective source concurrency and timeout budgets

#### Scenario: Settings change while run is active
- **WHEN** discovery execution settings change after a run is created
- **THEN** the active run continues with its captured policy
- **AND** a subsequently created run captures the updated policy and settings fingerprint

#### Scenario: Legacy run has no policy fields
- **WHEN** a client reads a terminal run created before execution-policy snapshots were persisted
- **THEN** the run remains readable
- **AND** absent effective budget fields are represented compatibly rather than fabricated

### Requirement: Draft sources execute with bounded per-run concurrency
The system SHALL execute source preparation, reuse reconstruction, and unresolved candidate analysis with no more concurrent source tasks than the captured per-run source-concurrency limit.

#### Scenario: More sources exist than the concurrency limit
- **WHEN** a run contains more eligible sources than its captured source-concurrency limit
- **THEN** no more than that number of source tasks execute concurrently
- **AND** remaining source tasks wait without consuming their per-source execution timeout before they start

#### Scenario: Multiple draft runs execute
- **WHEN** the global draft-analysis executor runs multiple durable runs concurrently
- **THEN** each run independently respects its captured per-run source limit
- **AND** the global run executor continues to enforce its active-run and queue bounds

#### Scenario: Reusable and unresolved sources are mixed
- **WHEN** a run contains exact-match reusable sources and sources requiring model calls
- **THEN** reusable sources are reconstructed without model calls
- **AND** unresolved source tasks remain subject to the same captured concurrency and deadline policy

### Requirement: Draft analysis enforces source and request deadlines
The system SHALL enforce each source deadline from the source task's actual start and the request deadline from successful run claim, SHALL request cancellation of expired work, and SHALL reject results that arrive after their applicable deadline.

#### Scenario: One source exceeds its deadline
- **WHEN** a started source task has not completed before its captured per-source deadline
- **THEN** the source receives one retryable source-deadline outcome
- **AND** cancellation is requested
- **AND** a later task completion cannot replace the timeout outcome

#### Scenario: Request deadline expires
- **WHEN** the overall request deadline expires before all source tasks are terminal
- **THEN** every unfinished queued or running source receives one retryable request-deadline outcome
- **AND** cancellation is requested for unfinished work
- **AND** the run finishes as `PARTIAL` or `FAILED` according to its accepted source outcomes

#### Scenario: Invalid-output retry has insufficient budget
- **WHEN** a source's first model output is unusable but the captured source or request deadline has expired before another output attempt can start
- **THEN** the system does not start the additional output attempt
- **AND** the source receives the applicable retryable deadline outcome

#### Scenario: Provider work completes after cancellation
- **WHEN** an underlying provider request ignores interruption and returns after the source or request deadline
- **THEN** its result is discarded
- **AND** it cannot persist a second result, contribute candidates, or alter the aggregate

### Requirement: Concurrent completion preserves durable deterministic results
The system SHALL persist accepted source progress independently while ordering successful aggregate inputs by the captured source snapshot rather than task completion order.

#### Scenario: Sources finish in different orders
- **WHEN** equivalent runs receive the same source results in different task completion orders
- **THEN** their deterministic candidates, conflicts, warnings, and schema projection are equivalent
- **AND** each run records exactly one terminal result for every source snapshot

#### Scenario: Client polls while work is active
- **WHEN** some source tasks have completed while others are queued or running
- **THEN** already accepted source outcomes are durably visible through run polling
- **AND** unfinished sources are not reported as successful or failed until the coordinator closes them

#### Scenario: Timed-out run is retried unchanged
- **WHEN** a client retries a partial or failed run without changing reuse-key inputs
- **THEN** matching successful outcomes are reused
- **AND** timed-out eligible sources execute under the new run's captured execution policy

