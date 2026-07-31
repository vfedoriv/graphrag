## 1. Citation and Result Contracts

- [x] 1.1 Define versioned answer, confidence, limitation, claim, evidence, graph fact, and diagnostic result schemas.
- [x] 1.2 Build stable run-local citation catalogs with typed text-child, graph-parent, and context-only mappings.
- [x] 1.3 Add complete result validation before JSONB persistence and after readback.

## 2. Synthesis and Repair

- [x] 2.1 Implement bounded prompts that delimit retrieved document text as untrusted data.
- [x] 2.2 Implement structured synthesis using the active profile and remaining run deadline.
- [x] 2.3 Validate claim citations, graph fact/evidence references, confidence, and limitations.
- [x] 2.4 Implement one deadline-gated repair call and partial/insufficient-evidence fallback.

## 3. Observability and Verification

- [x] 3.1 Add workflow/child observations and content-free retrieval, ranking, follow-up, citation, abstention, cancellation, deadline, and token metrics.
- [x] 3.2 Test that normal logs never expose queries, document text, prompts, model output, schemas, or graph payloads.
- [x] 3.3 Add deterministic end-to-end cases for valid claims, contradictions, prompt injection, unknown citations, successful repair, failed repair, and no evidence.
- [x] 3.4 Verify every fixture substantive claim against cited evidence and run `graphify update .`.
