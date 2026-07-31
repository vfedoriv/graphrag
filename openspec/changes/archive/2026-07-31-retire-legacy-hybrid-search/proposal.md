## Why

Once durable advanced search is production-ready, keeping the synchronous hybrid endpoint duplicates behavior, preserves obsolete `MENTIONS` traversal, and leaves conflicting settings and DTO contracts. A separate final change makes the breaking removal explicit and easy to defer or roll back.

## What Changes

- **BREAKING** Remove `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/hybrid-search` and its public DTOs.
- Remove `HybridSearchService` after reusable retrieval, expansion, and evidence components have migrated to advanced-search contracts.
- Remove advanced-path and legacy `MENTIONS` behavior and obsolete hybrid settings.
- Migrate compatible persisted overrides to advanced-search equivalents; explicitly retire settings without equivalent semantics.
- Update README, OpenAPI, `AGENTS.md`, and `CLAUDE.md`, and require evaluation/readiness gates before endpoint removal.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `hybrid-search`: Removes the legacy synchronous endpoint and all remaining contract requirements.
- `runtime-application-settings`: Migrates compatible hybrid overrides and removes obsolete hybrid keys.

## Impact

This removes controller methods, DTOs, service wiring, tests, properties, settings-catalog entries, and `MENTIONS`-based fixtures. It is proposal 7 of 7 and must not be applied until proposals 1–6 meet the documented retrieval, citation, deadline, and failure-tolerance acceptance gates.
