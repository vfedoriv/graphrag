## Context

Explicit knowledge-base creation assigns a default AI profile, whereas schema activation can create a knowledge base directly with incomplete defaults. Documents store a knowledge-base ID as a property rather than an ownership relationship, so deleting a knowledge-base node does not clean or reject its documents. Filesystem storage is also outside the Neo4j transaction boundary.

## Goals / Non-Goals

**Goals:**
- Make every supported knowledge-base provisioning path create the same complete initial state.
- Prevent inaccessible documents and binaries after knowledge-base deletion.
- Make upload, replacement, and deletion storage side effects durable and recoverable.
- Migrate legacy documents and knowledge bases without data loss.

**Non-Goals:**
- Implement destructive cascade deletion for knowledge bases in this change.
- Replace local filesystem storage or make document processing asynchronous.
- Change schema activation's single-active-schema behavior.

## Decisions

### Use one lifecycle service for provisioning

Introduce one transactional knowledge-base lifecycle service that creates a knowledge base with its default profile and required timestamps. Explicit creation and schema activation use it idempotently. This is preferred over duplicating profile-seeding logic because all future provisioning paths inherit the same invariant.

### Reject deletion while documents exist

Knowledge-base deletion returns a conflict with a document count when owned documents remain. Rejecting is selected over silent orphaning and over immediate cross-store cascade, which cannot be atomic. A future explicit purge workflow can build on storage reconciliation.

### Persist storage mutation intents

Record a storage mutation intent before or alongside document metadata changes, execute idempotent filesystem work, and persist completion or compensating state. A startup and scheduled reconciler retries unfinished mutations and reports failures. This is a local transactional-outbox pattern without a new broker dependency.

## Risks / Trade-offs

- [Deletion is a breaking API behavior] → Return RFC 7807 conflict details and document the required document cleanup workflow.
- [Legacy document IDs may reference absent knowledge bases] → Backfill complete knowledge bases through the lifecycle service before validation is enforced.
- [Filesystem operation succeeds but database commit fails] → Reconciler detects the unfinished intent and compensates or completes it idempotently.
- [Additional persistent operational records] → Retain completed mutation records for a bounded diagnostic period and expose metrics.

## Migration Plan

1. Seed the default profile before running lifecycle migration.
2. Backfill missing knowledge bases referenced by existing documents with the common provisioning service.
3. Add storage mutation records and reconciliation while retaining current storage paths.
4. Enforce ownership checks and reject non-empty knowledge-base deletion after migration validation.

## Open Questions

- What retention period and operational endpoint are appropriate for completed storage mutation records?
- Should a future KB purge API be synchronous for small data sets or always asynchronous?
