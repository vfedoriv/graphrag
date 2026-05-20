## Context

The current schema-generation path asks the LLM to emit node metadata, including `description`, `key`, and useful properties, then transforms that output into generated schema JSON. Review findings show that this path can fail hard on imperfect LLM output, can produce invalid key/property combinations, and contains a broken prompt sentence that weakens key guidance.

The graph persistence path builds stable Neo4j IDs by concatenating schema id, label/type, key display text, and key values with `|`. Composite key values are also joined with `|`, which makes IDs ambiguous when source values contain the delimiter. Relationship endpoint validation also checks only whether endpoint key maps are non-empty, not whether every required component of a composite key exists.

The repository already treats saved schemas as validated JSON and keeps generated schemas reviewable rather than auto-persisted. This change should harden the generation and persistence paths without changing public endpoint response shapes.

## Goals / Non-Goals

**Goals:**

- Make generated schema key guidance grammatically complete and test-covered.
- Ensure generated schema key candidates are either declared properties or reported/handled consistently before clients try to save the schema.
- Prevent delimiter-based stable ID collisions for extracted nodes and relationships.
- Validate every component of composite node and relationship endpoint keys before writing graph data.
- Persist only schema-declared domain properties on extracted nodes and relationships.
- Add focused regression coverage for the review findings that are valid on `dev`.

**Non-Goals:**

- No migration of already persisted extracted graph IDs.
- No endpoint contract change for schema-generation responses.
- No new external hashing or serialization dependency.
- No broad redesign of LLM prompt-injection defenses beyond key/property robustness and schema-constrained writes.

## Decisions

### Use canonical identity material plus SHA-256 for stable IDs

Build node and relationship IDs from explicit identity material instead of delimiter-concatenated strings. For nodes, identity material includes schema id, node label, ordered key names, and ordered key values. For relationships, identity material includes schema id, relationship type, source node id, and target node id. Serialize this material deterministically and hash it with SHA-256, optionally prefixing with a readable kind such as `node:` or `rel:`.

Rationale: escaping delimiters is easy to get wrong and changing separators only moves the collision problem. A deterministic digest avoids ambiguous parsing and keeps ID length bounded.

Alternative considered: length-prefix every segment and keep readable IDs. This is valid but more custom parsing logic than needed because IDs are only used as stable internal identifiers.

### Validate full key component presence before persistence

Treat normalized schema key names as the authority for required node identity. A node is valid only when every key component has a non-blank value. A relationship endpoint is valid only when every key component required by the endpoint node label exists and is non-blank after normalization/fill attempts.

Rationale: `GraphWriteService` should not be the first place that discovers missing identity material, and it should never generate IDs from partial composite keys.

Alternative considered: allow blank components and rely on generated fallback keys. This preserves throughput but creates fragile identities and silent merge behavior.

### Filter persisted domain properties by schema definitions

Before `SET n += $props` and `SET r += $props`, build property maps from allowed schema property names plus system metadata fields. Unknown extracted properties should be dropped before persistence and logged at warning or debug level with sanitized context.

Rationale: extraction is intended to be schema-constrained. Parameter binding prevents Cypher injection, but unfiltered property maps still let model output expand the stored graph shape beyond the active schema.

Alternative considered: reject the whole extraction when unknown properties appear. Dropping unknown properties is more consistent with existing tolerance for invalid relationship triples and unknown JSON fields.

### Keep generated schemas reviewable but make invalid key inference safer

Generated schema endpoints should still return generated content with advisory warnings. However, generation should not fabricate `id` as a key when no `id` property exists. Explicit LLM-provided key candidates should be normalized and checked against declared properties before being placed into the generated node definition, with warnings for discarded or missing candidates.

Rationale: generated schemas are not persisted automatically, but returning avoidably invalid schemas wastes user review time and conflicts with schema validation rules.

Alternative considered: make generation fail whenever key/property mismatch occurs. That would contradict the existing advisory requirement and increase brittleness for LLM output.

### Keep transformer contract enforcement scoped and test-covered

The transformer currently hard-fails when node property maps omit `description`, `key`, or useful properties. This behavior can remain for schema-generation output shape if it is intentional, but its boundary cases must be explicit and covered by tests. If implementation keeps hard-fail behavior, API errors should avoid leaking raw model-controlled key names unnecessarily.

Rationale: the project intentionally tightened schema-generation output shape, but the review showed important gaps in tests and error-message hygiene.

Alternative considered: convert all transformer contract violations into warnings. This can be done later if product behavior favors tolerance, but this proposal focuses on the critical data-integrity issues first.

## Risks / Trade-offs

- Existing extracted nodes are not re-keyed -> Old and new extracted graph IDs may coexist after deployment. Mitigation: scope the change to new writes and document that no migration is performed.
- Hash-based IDs are less human-readable -> Debugging raw Neo4j IDs is harder. Mitigation: keep label/type, schema id, key names, source document id, and extraction run id as properties for inspection.
- Dropping unknown properties may hide useful model output -> Some inferred data will not be persisted. Mitigation: log dropped property names and rely on schema updates for intentionally supported properties.
- Stricter endpoint key validation can reject previously accepted partial payloads -> Some extractions may fail rather than create partial identities. Mitigation: keep endpoint key fill logic for unambiguous single-node cases before validation.
- Generated schema key filtering may produce nodes with no key -> Schema validation will reject such schemas if saved. Mitigation: return advisory warnings that explain the missing key and required user action.
