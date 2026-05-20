## Why

Service classes currently mix orchestration with low-level parsing, validation, filtering, normalization, and formatting helpers. This makes core service behavior harder to read and prevents focused unit tests for reusable support logic that is currently hidden behind private methods.

## What Changes

- Extract cohesive private helper logic from oversized `*Service` classes into package-scoped or public utility/support classes with narrow responsibilities.
- Prioritize helpers that are deterministic and independently testable, such as extraction validation/filtering, Cypher reference parsing, graph write identity/property filtering, schema generation normalization, and extraction cleanup result handling.
- Keep service classes responsible for orchestration, repository/client interaction, transaction boundaries, and logging decisions.
- Add unit tests for the extracted helper classes, covering existing edge cases and preserving behavior.
- Preserve all existing public API contracts, persistence semantics, validation outcomes, and error formats.

## Capabilities

### New Capabilities
- `service-helper-utilities`: Defines internal service helper extraction, behavior preservation, and unit-test coverage expectations for deterministic service support logic.

### Modified Capabilities

## Impact

- Affected code: service-layer classes under `src/main/java/io/github/vfedoriv/graphrag/service`, graph service classes under `src/main/java/io/github/vfedoriv/graphrag/graph`, and any new helper classes colocated in appropriate packages.
- Affected tests: new focused unit tests for extracted utilities plus existing service, graph, and integration tests to guard behavior preservation.
- APIs: no REST API, DTO, database schema, or configuration changes expected.
- Dependencies: no new runtime dependencies expected.
