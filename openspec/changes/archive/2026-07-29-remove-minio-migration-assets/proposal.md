## Why

The Langfuse stack now runs and has been accepted against Garage, so retaining MinIO migration and rollback tooling presents an obsolete operational path as if it were still supported. Removing that path makes Garage the only documented and testable local object store while preserving the archived change as the historical migration record.

## What Changes

- **BREAKING** Remove the migration-only MinIO Compose overlay and the MinIO rollback overlay.
- **BREAKING** Remove the MinIO-to-Garage migration script and operational runbook.
- Remove active README and contributor-guidance instructions for preserving, starting, migrating from, or rolling back to MinIO.
- Retire the main-spec requirement that the repository provide a repeatable MinIO migration and rollback procedure, and remove remaining MinIO wording from the active Garage contract.
- Keep the Garage deployment, initializer, smoke test, event/media configuration, and local filesystem application-binary scope unchanged.
- Keep archived OpenSpec artifacts intact as historical product and migration records.
- Replace MinIO-specific negative test assertions with positive assertions for the complete Garage-only configuration.
- Require active product, operational, contributor, and test content to contain no MinIO references; OpenSpec change records and generated graph history remain historical evidence.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-observability-monitoring`: Remove the completed transitional MinIO migration/rollback contract and express local Langfuse object-storage requirements entirely in Garage terms.

## Impact

- Deletes `compose.langfuse-migration.yaml`, `compose.langfuse-minio-rollback.yaml`, `scripts/migrate-langfuse-minio-to-garage.sh`, and `docs/langfuse-minio-to-garage.md`.
- Updates `README.md`, `AGENTS.md`, and `CLAUDE.md` to describe only the Garage-backed Langfuse stack and its persistent volumes.
- Updates Compose-focused tests to remove migration-asset and MinIO-specific negative assertions while preserving positive Garage wiring coverage.
- Updates the `ai-observability-monitoring` main specification through a delta spec.
- Does not delete Docker containers or volumes, change GraphRAG APIs, change Garage data, or alter GraphRAG local filesystem binary storage.
