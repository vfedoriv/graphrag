## Context

Draft creation through review includes many operational node types and relationships: drafts, owned sources, immutable source revisions, analysis runs, per-source results, aggregate revisions, conflicts, decisions, and storage mutations. The workflow also uses a lease node for mutually exclusive analysis. These structures are relational histories and queues rather than graph facts.

## Goals / Non-Goals

**Goals:**

- Move draft lifecycle, sources, analysis, aggregation, conflicts, decisions, navigation, and recovery to PostgreSQL.
- Preserve revision currentness, deterministic fingerprints, retry lineage, and ownership boundaries.
- Replace the lease node with an atomic relational claim.

**Non-Goals:**

- Migrating evaluation, publication, or reprocessing state.
- Changing model prompts, generated schema format, or review API contracts.
- Converting payload text to JSONB.

## Decisions

1. **Use normalized workflow tables.** Child histories reference their draft/run/revision owners with cascading foreign keys. Uniqueness covers source revisions, aggregate revisions, decision sequences, and reuse identities. Mutation journals do not cascade with their filesystem target.

2. **Keep assigned IDs and text payloads.** Existing stable IDs continue across APIs and later workflow tables. Raw source-derived JSON/text remains text so current hashes, fingerprints, and deterministic comparisons do not change.

3. **Claim analysis on the draft row.** Starting an analysis conditionally sets `schema_draft.running_analysis_run_id` only when no active claim exists and the expected draft version/currentness still matches. Completion or recovery clears it conditionally. No standalone lease entity remains.

4. **Use bounded relational projections for navigation.** Draft list/detail summaries fetch current analysis, retry lineage, conflict/decision counts, and downstream placeholders without N+1 repository access. Evaluation/publication/reprocessing fields remain absent until the next change.

5. **Checkpoint external work.** A relational run/source-revision record is committed before model or filesystem work. Results and terminal state commit afterward. Recovery treats stale claims and partial results deterministically.

## Risks / Trade-offs

- [A worker loses its claim while a model call continues] → Completion updates require the same run identity and current claim; stale output cannot overwrite a newer run.
- [Large aggregate histories slow navigation] → Add currentness/owner/status indexes and bounded summary projections.
- [Cascades erase audit data unexpectedly] → Cascade only workflow-owned children; keep mutation journals and published schema references independent.
- [Intermediate workflow summaries span old and new stores] → Stop this change at the evaluation boundary and use explicit nullable downstream summaries until the next ordered change.

## Migration Plan

1. Add draft-analysis tables, constraints, foreign keys, and worker indexes.
2. Add entities, repositories, projections, and atomic claim queries.
3. Migrate lifecycle and source/storage workflows.
4. Migrate analysis, retry, recovery, aggregation, conflict, decision, and review behavior.
5. Migrate navigation projections and integration tests.
6. Retain evaluation/publication/reprocessing adapters until their dedicated change.

Rollback assumes disposable GraphRAG state; source-file journals must be reconciled before application rollback.

## Open Questions

- None.
