## Context

The repository includes three human-facing guidance documents with overlapping but different audiences: `README.md` for users and contributors, `AGENTS.md` for coding agents, and `CLAUDE.md` for Claude Code. The current implementation exposes API endpoints and configuration defaults that are not represented consistently across those files, including schema example-generation endpoints, knowledge-base schema listing, multipart request shapes, and extraction limit defaults.

## Goals / Non-Goals

**Goals:**
- Bring the three guidance documents into alignment with the current implementation.
- Document the implementation details that are likely to cause incorrect usage when stale.
- Keep the changes documentation-only, with no runtime or API behavior changes.

**Non-Goals:**
- Changing controller behavior, request payloads, or configuration defaults.
- Reorganizing the whole documentation set or introducing a docs site.
- Expanding OpenSpec coverage beyond the documentation-alignment concern needed for this change.

## Decisions

- Update all three repository documents in the same change.
  Rationale: the drift affects different audiences, and leaving one document stale would preserve conflicting guidance.
  Alternative considered: update only `README.md`. Rejected because agent instructions would still reflect incomplete API context.

- Treat documentation alignment as a standalone OpenSpec capability.
  Rationale: this creates an explicit requirement that repo guidance match the implemented API and configuration surface.
  Alternative considered: track this as an ad hoc docs edit with no spec. Rejected because the user explicitly requested an OpenSpec proposal and the drift is repeatable.

- Limit the changes to implementation-backed facts that can be verified in code.
  Rationale: this avoids speculative cleanup and keeps the proposal reviewable.
  Alternative considered: broader editorial rewriting. Rejected because it adds churn without improving correctness.

## Risks / Trade-offs

- Documentation remains manually maintained after this change. → Keep the scope focused on the highest-risk drift points and make alignment a tracked capability.
- Some guidance is duplicated across `README.md`, `AGENTS.md`, and `CLAUDE.md`. → Preserve duplication where audience-specific context matters, but ensure shared facts are updated together.
- A docs-only spec is less common than product behavior specs. → Phrase requirements in testable terms around document accuracy and synchronization.
