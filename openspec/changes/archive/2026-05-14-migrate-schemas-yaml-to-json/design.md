## Context

The application currently treats YAML as the primary schema authoring and generation format across schema parsing, validation, bootstrap loading, and generation endpoints. This creates a format contract that diverges from JSON-centric tooling and payload expectations. The migration target is a breaking transition to JSON-only behavior while preserving existing schema domain semantics.

## Goals / Non-Goals

**Goals:**
- Enforce JSON as the only accepted schema definition format for schema API operations and generation outputs.
- Persist newly created schema definitions with `SchemaFormat.JSON`.
- Replace bootstrap and test fixture resources with JSON files.
- Remove YAML format support from runtime contracts and enum values.

**Non-Goals:**
- Supporting dual-format YAML+JSON parsing during normal runtime.
- Automatic in-place migration of existing Neo4j YAML schema records.
- Changing the schema object model (entities, relationships, properties).

## Decisions

1. Remove runtime YAML parsing and switch parser/validator entry points to JSON-specific behavior.
- Rationale: single format contract reduces ambiguity and maintenance overhead.
- Alternative considered: keep transparent YAML fallback parsing. Rejected because it weakens format enforcement and prolongs migration complexity.

2. Remove `SchemaFormat.YAML` immediately and require manual cleanup of legacy YAML schema data before upgrade.
- Rationale: the migration is intentionally breaking, and the operator will clear legacy YAML records out-of-band.
- Alternative considered: keep YAML enum for compatibility checks. Rejected to avoid preserving deprecated format branches.

3. Convert all first-party schema assets to JSON (`classpath:/schemas/*.json`) and update tests/docs to match.
- Rationale: prevents drift between runtime behavior and examples/tests.
- Alternative considered: keep YAML fixtures and convert in tests. Rejected because it masks real runtime contract.

## Risks / Trade-offs

- [Breaking clients that send YAML to schema endpoints] -> Mitigation: update API documentation/examples and return explicit validation errors.
- [Upgrade with leftover YAML schema data in Neo4j] -> Mitigation: enforce pre-upgrade operational step to remove legacy YAML records before starting upgraded runtime.
- [Missed YAML references in tests/docs] -> Mitigation: targeted grep pass and full test suite execution.

## Migration Plan

1. Switch parser, registry, generation, and bootstrap flows from YAML handling to JSON-only handling.
2. Convert bundled bootstrap schemas and test fixtures to JSON.
3. Update API docs, DTO examples, README, and developer guidance to JSON terminology.
4. Execute a pre-upgrade operational cleanup to remove previously persisted YAML schema records from Neo4j.
5. Run full tests and release with explicit breaking-change notes.

## Open Questions

None.
