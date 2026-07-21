## 1. Legacy Mapper Configuration

- [x] 1.1 Register Jackson 2 `JavaTimeModule` on the shared legacy `ObjectMapper` and disable timestamp-form date serialization.
- [x] 1.2 Add focused regression coverage that uses the application legacy-mapper configuration to canonicalize a non-empty `List<DecisionResponse>` and asserts its ISO-8601 `createdAt` value.

## 2. Evaluation Snapshot Regression Coverage

- [x] 2.1 Extend schema-draft lifecycle integration coverage to record a decision, start an evaluation with an eligible held-out document, and verify the durable run contains the complete canonical decision snapshot and timestamp.
- [x] 2.2 Run the focused mapper and schema-draft evaluation tests and correct any regressions.

## 3. Verification and Knowledge Graph

- [x] 3.1 Run the full Maven test suite with `./mvnw test`.
- [x] 3.2 Run `graphify update .` after implementation to refresh the repository knowledge graph.
