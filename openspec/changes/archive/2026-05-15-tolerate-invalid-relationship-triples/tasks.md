## 1. Relationship Validation Tolerance

- [x] 1.1 Update graph extraction relationship validation to drop schema-invalid relationship triples instead of throwing chunk-level validation failure.
- [x] 1.2 Add warning logs for dropped relationships including schema name, chunk/run context, and `type|fromLabel|toLabel`.
- [x] 1.3 Keep existing strict node-label validation and verify unknown labels still fail validation.

## 2. Prompt Hardening

- [x] 2.1 Extend extraction prompt generation to include explicit allowed relationship triples from active schema.
- [x] 2.2 Add explicit prompt instruction: if no listed triple applies, omit the relationship from output.

## 3. Test Coverage

- [x] 3.1 Add/extend validation tests to confirm invalid relationship triples are filtered while valid data remains processable.
- [x] 3.2 Add/extend validation tests to confirm unknown node labels still fail.
- [x] 3.3 Add/extend extraction client prompt tests to verify allowed-triples block and omit rule are present.
- [x] 3.4 Run targeted extraction and processing tests.
