## Why

The optional local Langfuse stack currently depends on a MinIO service for S3-compatible event and media storage. Replacing it with Garage standardizes the local object-storage dependency on the selected self-hosted S3 project while preserving Langfuse behavior and existing stored observability objects during migration.

## What Changes

- Replace the profile-gated `langfuse-minio` Compose service and volume with a pinned Garage service using persistent metadata and data volumes.
- Provision a Garage cluster layout, `langfuse` bucket, and scoped access key before Langfuse starts using object storage.
- Reconfigure Langfuse web and worker event/media S3 settings for Garage's endpoint, region, credentials, and path-style access.
- Provide a repeatable MinIO-to-Garage object migration, verification, cutover, rollback, and delayed-retirement procedure that does not delete the existing MinIO volume during the rollback window.
- Update contributor documentation and Compose verification coverage for the Garage-backed Langfuse profile.
- Keep GraphRAG document and schema-draft binaries on the existing local filesystem backend; adding S3-compatible application binary storage is explicitly out of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-observability-monitoring`: Require the optional local Langfuse stack to use a persistent, health-checked Garage S3-compatible service and define migration-safe startup, endpoint, and data-preservation behavior.

## Impact

- Affects `compose.yaml`, Garage configuration/provisioning assets, Langfuse S3 environment variables, Compose-focused tests, and local Langfuse setup and migration documentation.
- Replaces MinIO-specific local environment variables, service names, health checks, and volumes with Garage equivalents.
- Does not change GraphRAG REST APIs, application runtime storage configuration, PostgreSQL document metadata, `BinaryStorageService`, or local document paths.
