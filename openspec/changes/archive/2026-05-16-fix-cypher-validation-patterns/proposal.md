## Why

Cypher validation incorrectly treats relationship type alternatives in relationship patterns as node labels, causing valid read queries to be rejected with errors such as `Unknown label: HAS_GREASE_RECOMMENDATION`. This blocks legitimate schema-constrained queries that use relationship unions.

## What Changes

- Refine Cypher schema reference extraction so node labels and relationship types are parsed from their own syntactic contexts.
- Support validation of relationship alternation such as `[:TYPE_A|TYPE_B]` and `[r:TYPE_A|TYPE_B]` without reporting the later alternatives as labels.
- Review `LABEL_PATTERN`, `REL_PATTERN`, and `PROPERTY_PATTERN` behavior against representative read-only Cypher forms.
- Add focused unit tests for valid and invalid label, relationship type, and property references, including relationship unions, aliases, backtick-quoted names, and common query shapes.
- Preserve existing blocked keyword, schema allow-list, `EXPLAIN`, and auto-`LIMIT` behavior.

## Capabilities

### New Capabilities
- `cypher-validation`: Defines expected Cypher schema reference validation behavior for labels, relationship types, and properties.

### Modified Capabilities

## Impact

- Affects `CypherValidationService` schema reference extraction and error reporting.
- Adds tests in `CypherValidationServiceTest` and, if useful, integration coverage in `CypherValidationIntegrationTest`.
- No API contract, dependency, or persistence changes are expected.
