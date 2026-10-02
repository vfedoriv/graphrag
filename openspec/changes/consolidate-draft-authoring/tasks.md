## 1. Establish draft ownership

- [ ] 1.1 Inventory draft authoring records, repositories, services, API mappings, and exact foreign edges; verify the package/exception inventory against `ArchitectureBoundaryTest` and the step-6 scope in `design.md`.
- [ ] 1.2 Move draft lifecycle, source/revision, analysis, aggregate, conflict/decision, and storage-journal records and repository ports/adapters under `schemas.drafts`; verify entity/query names, columns, constraints, and existing relational repository tests remain compatible.
- [ ] 1.3 Move lifecycle, source management, storage mutation/reconciliation, review/conflict, checkpoint, and retry helpers under `schemas.drafts`; verify focused service and storage recovery tests preserve revision admission, cleanup, and decision behavior.
- [ ] 1.4 Move draft authoring controller/API mapping and DTO ownership as needed without changing routes, JSON, validation, or OpenAPI; verify draft API contract tests.

## 2. Replace foreign implementation access

- [ ] 2.1 Add scoped document metadata/fingerprint and content/parsing consumer ports and public capability support only where existing `DocumentSourceInputs` is insufficient; verify owned/missing/foreign references, defensive copies, and no path or persistence-record exposure.
- [ ] 2.2 Migrate source add/refresh and analysis preparation to those ports through mapping-only bootstrap adapters; verify stale/unavailable classification, source fingerprints, draft-owned binary handling, parse/limit ordering, and stable analysis chunk IDs.
- [ ] 2.3 Add or reuse registry and knowledge-base public facts for base schema association, exact stored definition/hash, managed KB admission, and active AI profile ID/revision; verify base validation, immutable diff baseline, same-scope status, and caller transaction participation.
- [ ] 2.4 Migrate lifecycle, review, and navigation off registry/knowledge-base implementation records and repositories; verify ownership-safe errors, currentness, and bounded batch draft summaries with focused tests.

## 3. Durable analysis and later-slice seams

- [ ] 3.1 Move analysis orchestration, source factory, run recovery, and owned persistence behind `schemas.drafts` while reusing discovery analysis/aggregation collaborators deliberately; verify deterministic analysis, cache keys, source ordering, deadlines, and unchanged synchronous discovery tests.
- [ ] 3.2 Preserve relational run claims, independently committed source outcomes, late-result rejection, aggregate promotion, retry lineage, and recovery through relocation; verify existing concurrent-claim and persistence integration tests under escalated execution.
- [ ] 3.3 Keep evaluation/reprocessing navigation projections bounded and record their exact transitional dependencies for steps 7/9 without exporting draft repositories; verify list/detail/history query behavior and no per-draft repository read regression.

## 4. Enforcement and integrated verification

- [ ] 4.1 Add architecture rules for `schemas.drafts` ownership, immutable boundary values, mapping-only adapters, and feature-to-bootstrap prohibition; verify forbidden foreign repository/client/transaction fixtures fail the rules.
- [ ] 4.2 Remove all exact step-6 entries from `FROZEN_DOCUMENT_EDGES` and `FROZEN_SCHEMA_BRIDGE_EDGES`, retain exact later-step entries, and reject added or stale exceptions; verify `ArchitectureBoundaryTest` passes.
- [ ] 4.3 Run `./mvnw test -Pfast`; verify draft, discovery, registry, document, and predecessor boundary tests pass.
- [ ] 4.4 Under escalated execution, run focused draft lifecycle/source/analysis/review integration tests and `./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest`; verify persistence, recovery, document and schema interactions, and public behavior.

## 5. Documentation and change readiness

- [ ] 5.1 Update matching portal pages, `docs/MODULARIZATION_DESIGN.md`, and overlapping `README.md`, `AGENTS.md`, and `CLAUDE.md` facts for completed step 6; verify later-step boundaries remain accurately described.
- [ ] 5.2 Update `src/site/site.xml` if any portal page is added; run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest` and `./mvnw site` to verify navigation and generated documentation.
- [ ] 5.3 Run `openspec validate consolidate-draft-authoring --strict` and review the remaining exact exceptions and step-7 handoff; verify the change artifacts and implementation agree.
