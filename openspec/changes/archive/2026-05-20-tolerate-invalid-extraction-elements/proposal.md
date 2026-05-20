## Why

The latest graph-identity hardening made extraction safer, but invalid model output can now abort an entire chunk when a validation exception is thrown. Extraction validation should protect persistence without discarding valid information that can be safely kept from the same response.

## What Changes

- Convert graph extraction validation from fail-fast validation of model output into a normalization and filtering step that returns only valid nodes and relationships.
- Skip extracted nodes with unknown labels, incomplete required key material, or other non-repairable schema violations instead of throwing for the whole payload.
- Skip relationships whose type/triple/endpoints/key material cannot be validated after supported normalization or endpoint-key fill attempts.
- Keep safe repair behavior for cases the system can resolve deterministically, such as filling missing key values from available node properties or unambiguous matching nodes.
- Log validation findings with sanitized context so dropped or repaired extraction elements are observable without blocking the run.
- Refactor extraction validation flow into smaller, clearly named methods that separate normalization, repair, validation, filtering, and reporting responsibilities.
- Preserve hard failures for invalid system inputs or unsafe persistence boundaries, such as a null extraction result, missing schema definitions, excessive payload size, or graph writes receiving incomplete identity material.

## Capabilities

### New Capabilities

- `graph-extraction-validation-tolerance`: Defines tolerant extraction validation, repair, filtering, and logging behavior for invalid extracted nodes and relationships.

### Modified Capabilities

- `graph-extraction-response-tolerance`: Extend tolerance beyond invalid relationship triples so schema-invalid extracted nodes and relationships are skipped while valid data continues.
- `graph-extraction-result-contract`: Change extraction validation output expectations from rejecting incomplete model elements to returning a sanitized result containing only valid elements.
- `graph-identity-persistence`: Clarify that persistence still rejects incomplete identity material as a defense-in-depth boundary after validation filtering.

## Impact

- Affected code: `GraphExtractionValidationService`, `GraphExtractionService`, `GraphWriteService`, `GraphExtractionValidationException`, and related graph extraction tests.
- API impact: no endpoint shape change is expected; extraction runs should complete with partial graph data more often instead of failing on recoverable invalid model output.
- Data impact: invalid extracted elements are omitted from persisted graph data; valid elements from the same chunk remain eligible for persistence.
- Observability impact: validation findings are logged with element kind, schema context, reason, and sanitized identifiers or property names where safe.
- Dependency impact: no new external dependency is expected.
