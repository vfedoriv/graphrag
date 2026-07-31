## Why

Ranked evidence is not yet a trustworthy answer. The public workflow needs bounded synthesis with claim-level referential integrity, explicit abstention, and privacy-safe observability before it can replace hybrid search.

## What Changes

- Synthesize answer text, confidence, limitations, and typed claims from the final bounded evidence set.
- Assign stable citation IDs and require every substantive claim to reference known evidence.
- Preserve text-child citations, graph extraction-parent citations, and synthesis-only expanded context as distinct types.
- Validate structured output, allow one bounded citation repair, and otherwise persist a partial/insufficient-evidence result.
- Add advanced-search workflow/child observations and content-free metrics without exposing prompts, responses, secrets, or executable Cypher.

## Capabilities

### New Capabilities

- `advanced-search-answering`: Evidence-grounded answer synthesis, citation validation and repair, abstention, and safe public result contracts.

### Modified Capabilities

- `ai-observability-monitoring`: Adds privacy-controlled advanced-search workflow observations and retrieval/answer metrics.

## Impact

This adds structured synthesis contracts, prompt boundaries, citation validators, one repair path, result persistence mapping, observability, and end-to-end answer fixtures. It is proposal 6 of 7 and depends on proposals 1–5; it does not remove the legacy endpoint.
