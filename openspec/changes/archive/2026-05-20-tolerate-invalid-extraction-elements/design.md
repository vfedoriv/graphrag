## Context

Graph extraction receives model-produced nodes and relationships, validates them against the active schema, then writes the validated result to Neo4j. Recent identity hardening correctly requires complete node keys and complete relationship endpoint keys, but `GraphExtractionValidationService` currently throws for several model-output problems: unknown node labels, missing node keys after normalization, empty relationship endpoint keys, and partial composite endpoint keys. A single bad element can therefore fail the whole chunk and prevent valid elements from being persisted.

The extraction path already has a tolerance precedent: schema-invalid relationship triples are logged and dropped while the rest of the payload continues. This change extends that model to extracted nodes and endpoint validation while keeping the write path strict as a defense-in-depth boundary.

## Goals / Non-Goals

**Goals:**

- Treat validation of model-produced extraction elements as a sanitize-and-filter stage.
- Keep valid nodes and relationships from the same chunk even when other extracted elements are invalid.
- Preserve deterministic repair behavior only where the system has enough information to repair safely.
- Log dropped and repaired elements with enough sanitized context to debug extraction quality.
- Refactor validation code so each method has a clear responsibility and tests map cleanly to behavior.
- Keep graph persistence strict so incomplete identity material cannot be written if validation misses a case.

**Non-Goals:**

- No public API response shape change.
- No persisted model or database schema migration.
- No attempt to invent relationship endpoints when multiple candidate nodes exist.
- No relaxation of schema constraints for labels, relationship triples, relationship endpoint labels, or persisted properties.
- No broad redesign of LLM prompting beyond tests required to preserve current behavior.

## Decisions

### Make extraction validation return a sanitized result

`GraphExtractionValidationService.validate` should continue returning `GraphExtractionResult`, but the returned result becomes the authoritative sanitized payload. The service should normalize repairable elements, drop invalid elements, log findings, and return valid data only.

Rationale: this preserves the current call shape in `GraphExtractionService` and keeps persistence behavior simple. The service name can remain stable, but implementation should be structured around filtering rather than fail-fast validation.

Alternative considered: catch `GraphExtractionValidationException` in `GraphExtractionService` and continue per chunk. That does not recover valid elements from the same payload and leaves the validation service with mixed responsibilities.

### Reserve exceptions for non-model-output failures

Validation should still throw for invalid process inputs and guardrails: null extraction result, null or structurally unusable schema, and payloads exceeding configured maximum entity or relationship counts. These are not individual model extraction mistakes and continuing could create operational or safety problems.

For schema-invalid extracted elements, validation should drop the element instead of throwing. Examples include unknown node labels, unrepaired missing node key components, invalid relationship triples, unknown relationship endpoint labels, empty endpoint keys, and incomplete endpoint key components.

Alternative considered: never throw from extraction validation. That would hide configuration or contract errors that should stop the run.

### Validate relationships against the kept node set

Relationship filtering should run after node normalization and filtering. A relationship should be kept only if its triple is allowed, both endpoint labels are known, endpoint keys are complete after supported fill attempts, and each endpoint can be matched to a kept node by label and required key values when the endpoint label appears in the payload.

Rationale: relationships pointing to nodes that were dropped due to invalid identity material are not safely persistable. This also avoids graph writes attempting to create endpoints from invalid or missing node context.

Alternative considered: allow relationships to create nodes even when node entries were dropped. That preserves more relationships but can reintroduce weak identity material and makes validation outcomes harder to reason about.

### Keep deterministic repair narrow

The service may repair missing node key properties from existing non-blank preferred properties only when the repair rule is deterministic and already supported. It may fill missing relationship endpoint key components only when exactly one kept node with the endpoint label exists and that node has the required component.

Rationale: deterministic repairs preserve useful extraction data without inventing ambiguous graph identity. Ambiguous repairs should be logged and skipped.

Alternative considered: generate fallback keys from hashes for any missing key. This keeps more nodes but risks unstable or meaningless identity, especially for composite schema keys.

### Introduce validation findings internally

Refactor validation around a small internal finding representation or helper methods such as `dropNode`, `dropRelationship`, and `logRepair`. Findings do not need a public API contract in this change, but logs should include schema name, element kind, reason code, label/type/triple, and sanitized key/property names or previews.

Rationale: a local finding model makes tests and logging consistent without introducing a new endpoint contract.

Alternative considered: return findings in API responses. Useful later, but it is a separate product/API change.

### Keep graph writes strict

`GraphWriteService` should continue rejecting incomplete identity material before ID derivation. Tests should verify that validation filters invalid elements before write, and write-level tests should preserve strict failure for direct invalid calls.

Rationale: validation tolerance must not weaken persistence invariants.

Alternative considered: make graph writes silently skip invalid elements too. That duplicates filtering logic at the persistence layer and makes dropped data harder to observe with extraction context.

## Risks / Trade-offs

- Some invalid model output will no longer fail extraction runs -> Mitigation: log every drop or repair with reason codes and counts so extraction quality regressions remain visible.
- Skipping nodes can cascade into skipped relationships -> Mitigation: validate relationships after node filtering and log endpoint-related drop reasons separately.
- Generated fallback keys may preserve low-quality identities -> Mitigation: keep repair rules narrow and prefer skipping when required identity cannot be derived from existing non-blank values.
- Logs could expose model-produced sensitive text -> Mitigation: use existing `LogSanitizer` previews and prefer labels, relationship types, property names, and reason codes over raw values.
- The method name `validate` may imply fail-fast behavior -> Mitigation: refactor with clear private method names and update tests/specs to define validation as sanitization for model output.
