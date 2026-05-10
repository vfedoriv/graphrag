## 1. Schema Mapping Fix

- [x] 1.1 Inspect `LangChain4jSchemaGenerationService.inferSchema` mapping flow and identify where node descriptions, node properties, and edge descriptions are dropped.
- [x] 1.2 Update node mapping to preserve non-blank descriptions and convert inferred node properties into schema property entries with stable type mapping/fallback.
- [x] 1.3 Update edge mapping to preserve non-blank relationship descriptions while keeping existing relationship type/source/target behavior.

## 2. Regression Coverage

- [x] 2.1 Add or update service-level tests to verify generated schema includes node descriptions and node properties when provided by transformer output.
- [x] 2.2 Add or update service-level tests to verify generated schema includes edge descriptions when provided by transformer output.
- [x] 2.3 Add or update tests to verify generation still succeeds when optional metadata is absent.

## 3. Validation

- [x] 3.1 Run targeted and full test suites covering schema generation behavior.
- [x] 3.2 Confirm generated YAML examples include preserved metadata and no regressions in existing schema generation endpoints.
