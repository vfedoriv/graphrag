## Context

Current schema generation accepts one text or file, asks the graph transformer for one `GraphDocument`, and maps that result directly to schema JSON. `SchemaGraphMapper` can combine repeated labels and relationship triples inside that one result, but it cannot retain per-source evidence or represent competing definitions. The separate example-generation call accepts free-form guidance and returns opaque JSON text that callers copy into the final generation request.

This change introduces a stateless discovery path that is useful on its own and defines the candidate contracts reused by persistent drafts. It must preserve the current global and knowledge-base-scoped generation endpoints, AI profile routing, schema validation rules, and privacy-safe observation behavior.

## Goals / Non-Goals

**Goals:**

- Analyze multiple representative sources independently in a bounded synchronous request.
- Make typed domain guidance part of candidate extraction and aggregation.
- Preserve source and chunk evidence without logging content.
- Produce deterministic candidates, conflicts, support counts, and review-only schema JSON.
- Establish reusable application-layer contracts for later persistent drafts.

**Non-Goals:**

- Persisting drafts, source results, decisions, or uploaded discovery files.
- Retrying a discovery request after the HTTP request ends.
- Publishing, activating, or reprocessing documents.
- Automatically resolving aliases, key conflicts, or breaking changes.
- Replacing the established single-source endpoints.

## Decisions

### Add dedicated discovery endpoints

Add `POST /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/discover` for JSON document/text sources and `POST /api/v1/knowledge-bases/{knowledgeBaseId}/schemas/discover/from-files` for multipart requests whose metadata can also reference documents and text samples. The operations are knowledge-base scoped because source ownership and the active AI profile are both knowledge-base concerns.

Alternative considered: expand `/schemas/generate`. Rejected because its required single `text` and `example` contract is already specified as backward compatible and its response lacks source/candidate semantics.

### Prepare an independent analysis document for every source

Existing document references are validated against the knowledge base, read from binary storage, parsed with `DocumentParsingService`, and divided into analysis chunks without persisting `DocumentChunk` or embedding data. Request files follow the same parser path. Text sources bypass parsing but still receive deterministic source and chunk identifiers derived from source ordinal and content SHA-256.

Analysis chunking uses a typed configuration snapshot and records its revision in the response. It does not require the existing document to have completed normal processing, and it does not reuse persisted chunks whose processing settings may differ.

Alternative considered: require processed documents and reuse `DocumentChunk`. Rejected because schema discovery should work before an active schema exists, while normal processing currently requires an active extraction contract.

### Use typed candidate containers as the model boundary

Introduce a top-level candidate-result object containing lists rather than a top-level array. Candidate kinds are node, node property, node key, relationship triple, and relationship property. Every candidate carries raw proposed identifiers, evidence location, confidence when available, and evidence-origin flags.

Spring AI structured output conversion supplies the JSON schema and converts the response. Provider-native structured output is enabled only when the resolved provider/model supports it; portable prompt-based conversion remains the baseline. The top-level container avoids provider limitations around top-level arrays.

Alternative considered: continue using `LLMGraphTransformerExt` and infer candidates from a merged `GraphDocument`. Rejected because its entity instances are an awkward persistence boundary for schema evidence and conflict alternatives.

### Keep semantic proposals in the model and merge mechanics in code

The model may propose aliases, canonical names, and inferred candidates. Application code performs only deterministic operations: identifier validation, configured normalization, candidate identity calculation, compatible union, support counting, conflict classification, suppression, and ordering. Alias proposals are review warnings, not automatic merges.

Canonical candidate identities are based on normalized coordinates:

- node: label;
- node property: node label plus property name;
- node key: node label;
- relationship: type plus from-label plus to-label;
- relationship property: relationship coordinates plus property name.

Alternative considered: ask a final LLM call to merge all source schemas. Rejected because it would lose deterministic conflict behavior and make reruns unstable.

### Separate evidence origin from review state

The stateless response exposes a set of origins (`OBSERVED`, `GUIDED`, `INFERRED`, and `EXISTING` when caller context is later supported) and a recommendation/conflict state. It does not pretend that a single origin describes all support for an aggregate candidate. Persistent acceptance, rejection, modification, and pinning are deferred to the draft change.

### Bound synchronous fan-out

Source analysis uses a request-scoped bounded executor with limits for source count, parsed characters/tokens, total request size, per-source chunks, and concurrent model calls. Results are collected in input source order before deterministic aggregation. Partial success is returned only when at least one source succeeds.

Alternative considered: introduce durable asynchronous jobs in Phase 1. Rejected to keep this first capability stateless; Phase 2 introduces durable runs using the same source analyzer.

## Risks / Trade-offs

- [Long synchronous requests can approach gateway timeouts] → Enforce conservative limits, bounded concurrency, per-call timeouts, and clear limit errors; direct larger workloads to persistent drafts once available.
- [Model confidence is not calibrated across providers] → Treat confidence as advisory and base deterministic support counts on independent source evidence.
- [Normalization can collapse intentionally distinct identifiers] → Keep normalization conservative, retain original identifiers, and surface alias suggestions without automatic semantic merges.
- [Request-scoped file evidence cannot be retrieved later] → Return source fingerprints and stable response-local evidence identifiers; durable evidence is provided by Phase 2.
- [Parallel calls increase provider throttling risk] → Use configurable bounded concurrency and classify retryable source failures in partial responses.

## Migration Plan

1. Add DTOs, typed candidate contracts, validation, and deterministic aggregation behind new endpoints.
2. Add the source preparation and model adapter path while retaining current generation collaborators.
3. Add limits and observation metadata with safe defaults.
4. Deploy without data migration because this change is stateless and additive.
5. Roll back by removing or disabling only the new discovery endpoints; existing generation remains unaffected.

## Open Questions

None. Persistence, durable retry, review decisions, and publication are deliberately assigned to the following changes.
