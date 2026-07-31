## Context

The preceding proposals yield a durable run with ranked child evidence, optional parent context, graph facts, planning coverage, and limitations. Existing citation-kind rules already distinguish precise text evidence, authoritative graph parents, and context-only parents.

This is proposal 6 of 7 and requires `add-adaptive-advanced-search-planning`.

## Goals / Non-Goals

**Goals:**

- Produce a bounded answer whose substantive claims reference known citations.
- Return usable evidence and explicit limitations when an answer cannot be validated.
- Observe quality, latency, fallback, and token use without leaking content to normal logs.

**Non-Goals:**

- Guarantee factual truth beyond supplied evidence.
- Cite synthesis-only expanded context or invent child citations for graph facts.
- Stream partial model output or expose raw prompts/model responses.

## Decisions

### Build a typed citation catalog before prompting

Final evidence receives stable run-local citation IDs. Text entries map to precise child/source spans; graph entries additionally map fact/evidence IDs to extraction-parent citations. Expanded parent text may be included as delimited context but has no claim-citable ID by itself.

### Treat retrieved text as untrusted data

Prompt sections clearly delimit system rules, user question, citation catalog, and document data. Instructions found inside retrieved text are explicitly non-authoritative. Excerpts and total tokens are bounded.

### Validate, repair once, then abstain

Structured output validation checks confidence, limitations, claim substance, citation existence/type, and graph fact references. One repair call receives validation errors and the same bounded catalog. A second failure returns `PARTIAL` with evidence and an answer-unavailable or insufficient-evidence outcome.

### Separate operational logs from AI content capture

Normal logs contain IDs, fingerprints, counts, timings, statuses, and error classes only. `AiObservationService` controls optional prompt/response capture. Metrics cover branch failures, overlap, rerank movement, follow-up, citations, abstention, cancellation, deadline exhaustion, and tokens.

## Risks / Trade-offs

- [Valid citation IDs still support an overbroad claim] → Add fixture-verifiable claim support tests and conservative abstention instructions.
- [Repair call consumes deadline] → Allow it only when bounded remaining time exists.
- [Evidence excerpts contain prompt injection] → Delimit and label them as untrusted data and test adversarial fixtures.

## Migration Plan

1. Add citation catalog and public result contracts.
2. Add bounded structured synthesis and validation.
3. Add one repair path and partial/abstention persistence.
4. Add workflow observations, metrics, and deterministic end-to-end fixtures.
5. Keep the endpoint alongside legacy hybrid search until proposal 7.

## Open Questions

None.
