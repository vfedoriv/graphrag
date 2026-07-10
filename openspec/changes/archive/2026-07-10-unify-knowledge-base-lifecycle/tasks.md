## 1. Lifecycle Invariants

- [x] 1.1 Implement a shared, idempotent knowledge-base provisioning service that seeds the default AI profile.
- [x] 1.2 Route explicit KB creation and implicit schema-activation provisioning through the shared lifecycle service.
- [x] 1.3 Enforce managed knowledge-base ownership for document upload, listing, processing, replacement, and deletion.
- [x] 1.4 Reject non-empty knowledge-base deletion with an RFC 7807 conflict response and document count.

## 2. Cross-Store Reconciliation

- [x] 2.1 Define persisted storage mutation state, repository access, retention, and indexes.
- [x] 2.2 Record and complete recoverable storage mutations for document upload, replacement, and deletion.
- [x] 2.3 Implement startup and scheduled reconciliation with idempotent retry, compensation, metrics, and content-safe logs.

## 3. Migration and Verification

- [x] 3.1 Backfill missing knowledge bases referenced by legacy documents after default-profile seeding.
- [x] 3.2 Add tests for explicit and implicit provisioning, default profile assignment, and non-empty deletion rejection.
- [x] 3.3 Add cross-store failure and reconciliation tests for upload, replacement, and deletion.
- [x] 3.4 Run focused lifecycle tests and the full Maven suite.
