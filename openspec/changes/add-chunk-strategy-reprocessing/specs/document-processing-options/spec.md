## ADDED Requirements

### Requirement: Immutable-plan overwrite processing
Document overwrite processing invoked by a migration worker SHALL accept the validated immutable plan snapshot and SHALL not resolve behavior-affecting chunk, tokenizer, profile, embedding-space, schema, or processing options from later live state.

#### Scenario: Worker processes current target
- **WHEN** a worker claims an item whose source and target remain current
- **THEN** overwrite processing uses the plan snapshot and commits that document independently
