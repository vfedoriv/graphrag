## 1. Baseline And Candidate Selection

- [x] 1.1 Run the current focused service and graph test suite to establish a pre-refactor baseline.
- [x] 1.2 Inventory private helper clusters in `GraphExtractionValidationService`, `CypherValidationService`, `GraphWriteService`, `GraphExtractionService`, and `LangChain4jSchemaGenerationService`.
- [x] 1.3 Select the first-pass extraction scope, prioritizing deterministic helpers with meaningful edge cases and avoiding logging-only or orchestration-only methods.

## 2. Extraction Validation Helpers

- [x] 2.1 Extract schema index, relationship triple, blank-value, first-non-blank, required-key, and endpoint matching logic from `GraphExtractionValidationService` into cohesive graph extraction support classes.
- [x] 2.2 Extract node normalization/filtering and relationship normalization/filtering decisions so the service can orchestrate validation and logging from structured outcomes.
- [x] 2.3 Add focused unit tests for extraction helper behavior covering unknown labels, invalid triples, key repair, incomplete keys, endpoint repair, endpoint mismatch, and null or blank properties.

## 3. Cypher Validation Helpers

- [x] 3.1 Extract Cypher node label, relationship type, relationship union, backtick trimming, property-map exclusion, and property reference parsing from `CypherValidationService` into Cypher-focused support classes.
- [x] 3.2 Extract schema allow-list construction and schema reference validation decisions where this can be done without moving repository or Neo4j `EXPLAIN` side effects.
- [x] 3.3 Add focused unit tests for Cypher helper behavior covering valid and invalid node labels, aliased relationship unions, relationship type ranges, property maps, backticked names, and qualified properties.

## 4. Graph Write Helpers

- [x] 4.1 Extract stable node id, stable relationship id, canonical identity material, SHA-256 hashing, complete identity validation, and safe schema token checks from `GraphWriteService` into graph write support classes.
- [x] 4.2 Extract declared node and relationship property allow-listing/filtering while keeping persistence queries and write orchestration in `GraphWriteService`.
- [x] 4.3 Add focused unit tests for graph write helpers covering deterministic ids, delimiter-safe canonicalization, incomplete identity rejection, unsafe token rejection, and declared property filtering.

## 5. Remaining Service Cleanup

- [x] 5.1 Extract small deterministic cleanup helpers from `GraphExtractionService`, such as cleanup row conversion and non-blank error message selection, if doing so reduces service noise without moving Neo4j cleanup execution.
- [x] 5.2 Evaluate schema generation normalization helpers in `LangChain4jSchemaGenerationService` and extract only cohesive deterministic logic that can be tested without model or Spring dependencies.
- [x] 5.3 Remove obsolete private helper methods from service classes after callers are migrated to the extracted support classes.

## 6. Verification

- [x] 6.1 Run focused unit tests for all new support classes.
- [x] 6.2 Run existing service and graph tests that cover graph extraction validation, Cypher validation, graph writes, extraction cleanup, schema generation, and document processing.
- [x] 6.3 Run `./mvnw test` and fix any regressions without weakening existing behavior assertions.
- [x] 6.4 Confirm no REST API, DTO, configuration, database schema, or dependency changes were introduced.
