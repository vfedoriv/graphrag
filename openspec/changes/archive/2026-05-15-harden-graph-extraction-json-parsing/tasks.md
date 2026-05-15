## 1. Parser Tolerance

- [x] 1.1 Update graph extraction response deserialization to ignore unknown JSON fields for extraction DTOs.
- [x] 1.2 Ensure tolerant parsing is scoped to extraction flow and does not change global ObjectMapper behavior.

## 2. Validation And Observability

- [x] 2.1 Keep and verify schema-constrained validation on parsed extraction output (labels, relationships, properties).
- [x] 2.2 Add warning logging for ignored unknown fields with chunk/extraction context and key names.

## 3. Test Coverage

- [x] 3.1 Add unit test for extraction response containing unknown node/relationship keys that must parse successfully.
- [x] 3.2 Add test proving schema violations still fail even when unknown fields are present.
- [x] 3.3 Run targeted test suite for graph extraction and document processing paths.
