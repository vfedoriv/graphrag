## 1. Contract Tests

- [x] 1.1 Update `GraphExtractionValidationServiceTest` so unknown node labels are skipped and valid nodes from the same payload remain.
- [x] 1.2 Add validation tests for unrepaired missing node key components being skipped instead of failing the whole payload.
- [x] 1.3 Update composite endpoint tests so unrepaired partial relationship endpoint keys are skipped and valid relationships remain.
- [x] 1.4 Add tests for relationships being skipped when they reference an endpoint identity that is not present in the kept node set.
- [x] 1.5 Add log-observability assertions where practical for dropped and repaired nodes or relationships.

## 2. Validation Refactor

- [x] 2.1 Refactor `GraphExtractionValidationService.validate` into small methods for guardrail checks, schema index creation, node normalization/filtering, relationship normalization/filtering, endpoint repair, and finding logging.
- [x] 2.2 Keep fatal validation exceptions for null extraction results and configured max node/relationship limit violations.
- [x] 2.3 Change node validation to drop unknown-label nodes and nodes with unrepaired incomplete required key components.
- [x] 2.4 Preserve deterministic node key repair only when a required key can be filled from supported non-blank node properties.
- [x] 2.5 Change relationship validation to drop invalid triples, unknown endpoint labels, empty endpoint keys, unrepaired partial endpoint keys, and relationships whose endpoints do not resolve to kept nodes.
- [x] 2.6 Ensure validation returns a sanitized `GraphExtractionResult` containing only persistable nodes and relationships.

## 3. Logging And Safety Boundaries

- [x] 3.1 Add consistent warning logs for dropped extraction elements with schema name, element kind, reason code, and sanitized context.
- [x] 3.2 Add consistent warning logs for deterministic repairs without logging unsafe raw model output.
- [x] 3.3 Preserve `GraphWriteService` strict rejection of incomplete node and relationship identity material before stable ID derivation.
- [x] 3.4 Ensure `GraphExtractionService` continues passing only the sanitized validation result to graph persistence.

## 4. Verification

- [x] 4.1 Run targeted graph extraction validation and graph write tests.
- [x] 4.2 Run `./mvnw test`.
- [x] 4.3 Run `openspec status --change "tolerate-invalid-extraction-elements"` and confirm the change is apply-ready.
