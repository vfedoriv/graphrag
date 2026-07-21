## 1. Persist deterministic baseline snapshots

- [x] 1.1 Add typed baseline metadata and immutable snapshot fields to schema-draft aggregate persistence.
- [x] 1.2 Capture the configured base schema or canonical empty baseline when an eligible first aggregate is promoted.
- [x] 1.3 Capture the effective projection and ID of the aggregate that is current immediately before a no-base successor is promoted.
- [x] 1.4 Ensure retained non-current aggregates cannot replace or alter the stored predecessor lineage.
- [x] 1.5 Add a deterministic compatibility resolver for legacy aggregates without stored baseline metadata.

## 2. Expand the diff contract

- [x] 2.1 Add the `BASE_SCHEMA`, `PREVIOUS_AGGREGATE`, and `EMPTY` baseline enum and typed response descriptor.
- [x] 2.2 Extend `DiffResponse` with `draftRevision` and the baseline descriptor while preserving existing diff items and ordering.
- [x] 2.3 Refactor diff calculation to read the stored snapshot when present and use the legacy resolver only when absent.
- [x] 2.4 Document baseline identifiers, nullability, content hashes, revision binding, and strict-client rollout behavior in OpenAPI.

## 3. Add regression coverage

- [x] 3.1 Add service tests for base-schema, previous-current-aggregate, and empty baseline descriptors and hashes.
- [x] 3.2 Add concurrency/lineage coverage proving a retained non-current aggregate is never selected as a promoted aggregate's predecessor.
- [x] 3.3 Add determinism coverage proving later base-schema edits and review decisions do not mutate a stored baseline snapshot.
- [x] 3.4 Add legacy aggregate and API contract tests while preserving compatibility classification and stable ordering assertions.

## 4. Validate the change

- [x] 4.1 Run focused schema-draft lifecycle, diff, and OpenAPI contract tests.
- [x] 4.2 Run the full Maven test suite and strict OpenSpec validation.
- [x] 4.3 Run `graphify update .` and verify `git diff --check`.
