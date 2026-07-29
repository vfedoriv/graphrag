## 1. Garage Deployment

- [x] 1.1 Verify the current Langfuse v3 S3 operations against a specific Garage release, pin that image, and select documented local defaults for region, node capacity, ports, and replication factor one.
- [x] 1.2 Add Garage configuration with persistent metadata and object-data directories, environment-overridable RPC/admin secrets, path-style S3 access, and local-development-safe bind addresses.
- [x] 1.3 Replace the normal-profile MinIO service and volume wiring in `compose.yaml` with the Garage service, separate metadata/data volumes, published S3 port, and process health check.
- [x] 1.4 Add an idempotent one-shot initializer that assigns and applies the single-node layout, creates the `langfuse` bucket and Garage key when absent, grants scoped permissions, and succeeds unchanged on repeated runs.

## 2. Langfuse Integration

- [x] 2.1 Gate Langfuse web and worker startup on healthy Garage and successful initializer completion.
- [x] 2.2 Replace MinIO-specific Langfuse environment variables with Garage-specific credential inputs, the configured Garage region, path-style mode, an internal event endpoint, and a separately configurable host-reachable media endpoint.
- [x] 2.3 Remove MinIO service dependencies and normal-profile MinIO configuration while ensuring the default non-Langfuse Compose startup remains unchanged.
- [x] 2.4 Add a migration-only Compose override or equivalent documented mechanism that can mount the existing MinIO volume concurrently with Garage without recreating or deleting source data.

## 3. Migration and Operations Documentation

- [x] 3.1 Document prerequisites, backup expectations, Garage initialization checks, and how to expose old MinIO data alongside Garage for migration.
- [x] 3.2 Document a non-destructive initial bucket copy, source/destination inventory comparison, and content-integrity verification that does not treat multipart ETags as universal content hashes.
- [x] 3.3 Document the write-quiesce window, final delta copy, Garage cutover, event/media acceptance checks, and rollback to the preserved MinIO volume.
- [x] 3.4 Document delayed explicit MinIO retirement, the prohibition on deleting the source volume during the rollback window, and the single-failure-domain limitation of replication-factor-one Garage.
- [x] 3.5 Update overlapping Langfuse and Compose guidance in `README.md`, `AGENTS.md`, and `CLAUDE.md` while keeping GraphRAG local document storage guidance unchanged.

## 4. Verification

- [x] 4.1 Update Compose configuration tests to require the pinned Garage service, metadata/data volumes, initializer dependency, Garage S3 settings, host-reachable media endpoint, and absence of a normal-profile MinIO dependency.
- [x] 4.2 Add or document an executable Garage smoke test covering first initialization, repeated initialization, bucket authorization, object put/get/list/delete, and persistence across container recreation.
- [x] 4.3 Start the complete Langfuse profile and verify trace/event ingestion plus browser-reachable presigned media upload and download against Garage.
- [x] 4.4 Exercise the migration procedure with representative existing MinIO objects, verify the initial and delta copies, and demonstrate rollback without modifying the source volume.
- [x] 4.5 Run the relevant Maven tests, validate the resolved Compose profiles, and run `graphify update .` after implementation changes.
