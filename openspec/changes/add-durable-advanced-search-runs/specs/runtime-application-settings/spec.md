## ADDED Requirements

### Requirement: Advanced-search lifecycle settings are typed
The runtime settings catalog SHALL expose validated `app.advanced-search.*` deadline, evidence, candidate, pool, graph, query-length, evidence-text, and retention settings as live values, and executor concurrency, queue capacity, branch concurrency, and full-text analyzer settings as restart-required deployment-managed values.

#### Scenario: Live deadline is updated
- **WHEN** an operator persists a valid live advanced-search deadline override
- **THEN** subsequent runs snapshot and use it without changing an already-created run

#### Scenario: Executor concurrency is inspected
- **WHEN** the settings API lists executor concurrency
- **THEN** it reports restart-required lifecycle metadata and does not claim live application

#### Scenario: Related settings are invalid together
- **WHEN** a bulk update makes default evidence exceed maximum evidence or a pool exceed its allowed bound
- **THEN** the entire update is rejected atomically
