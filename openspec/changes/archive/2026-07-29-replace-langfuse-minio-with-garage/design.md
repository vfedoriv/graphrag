## Context

The optional `langfuse` Docker Compose profile currently starts Langfuse web and worker with PostgreSQL, ClickHouse, Redis, and a MinIO service. Both Langfuse containers use MinIO through S3-compatible event and media settings; the media endpoint is host-reachable because Langfuse returns presigned URLs to browsers and SDK clients.

Garage is also S3-compatible, but it is not a drop-in container-image rename. A fresh Garage deployment needs persistent metadata and data directories, a cluster layout with a fixed replication factor, an assigned node/capacity, a bucket, and a key authorized for that bucket. Garage clients must use the configured region, and local Compose access is simplest with path-style requests.

This infrastructure is separate from GraphRAG's `BinaryStorageService`. GraphRAG document and schema-draft content remains stored on the configured local filesystem in this change.

## Goals / Non-Goals

**Goals:**

- Replace MinIO with a deterministic, profile-gated Garage service for local Langfuse event and media objects.
- Preserve the current default startup where the Langfuse stack is optional.
- Persist Garage metadata and data across container recreation.
- Initialize Garage idempotently before Langfuse relies on it.
- Preserve correct internal event access and externally reachable presigned media URLs.
- Provide a non-destructive migration, validation, cutover, rollback, and retirement runbook for existing MinIO objects.
- Keep local development credentials configurable and clearly non-production.

**Non-Goals:**

- Add an S3 adapter to GraphRAG application code.
- Move GraphRAG documents, schema-draft sources, or PostgreSQL `contentUri` values.
- Design a production multi-node Garage cluster or claim high availability from the local single-node deployment.
- Change Langfuse, OpenTelemetry, or GraphRAG API behavior.
- Automatically delete the existing MinIO container volume.

## Decisions

### Run a pinned single-node Garage deployment for the local profile

The Compose profile will use a pinned, tested Garage release with separate persistent volumes for metadata and object data. Its configuration will set a single-node replication factor of one, explicit S3 region, S3 API bind address, administration endpoint, and RPC secret.

Replication factor one matches the current local-development scope and avoids presenting a single host as highly available. Production topology remains deployment-specific because meaningful Garage durability requires multiple nodes and failure domains.

Alternatives considered:

- Use `latest`: rejected because an unbounded image update can change configuration or migration behavior.
- Configure a multi-node cluster in the repository Compose file: rejected because all nodes and volumes would still share one developer host while consuming more resources.
- Keep MinIO as a fallback in the normal profile: rejected because the goal is to remove the normal runtime dependency; coexistence belongs only to the migration path.

### Initialize layout, bucket, and credentials through an idempotent one-shot service

A profile-gated initialization service will wait for the Garage administration API, inspect the current state, assign the local node and capacity if necessary, apply the layout if necessary, create the `langfuse` bucket if absent, import or create the configured development key if absent, and grant only the bucket permissions Langfuse requires. Re-running initialization must succeed without recreating or revoking existing resources.

Langfuse web and worker will depend on successful initializer completion rather than only on the Garage process health check. Secrets and access credentials will support environment overrides, with obvious local-only defaults where deterministic startup requires them.

Alternatives considered:

- Require every developer to run Garage CLI commands manually: rejected because profile startup and tests would not be deterministic.
- Create the bucket implicitly from a mounted directory as the MinIO setup currently does: rejected because Garage bucket and key authorization are metadata operations, not directory conventions.
- Put initialization into Garage's main container command: rejected because lifecycle and failure reporting are clearer with a separate one-shot service.

### Use distinct internal event and host-reachable media endpoints

Langfuse event upload settings will use the Compose-network Garage endpoint. Media upload settings will use a configurable host-reachable endpoint because those values participate in presigned URLs returned to browsers and SDK clients. Both paths will use the Garage region and path-style access, avoiding local wildcard-DNS requirements.

The `langfuse` bucket remains dedicated to Langfuse. GraphRAG application objects will not share it in this change.

Alternatives considered:

- Use the Compose hostname for both endpoint families: rejected because browsers outside the Compose network cannot resolve it.
- Use virtual-hosted bucket addressing: rejected for the local profile because it adds wildcard DNS and TLS requirements without a corresponding benefit.

### Keep migration operator-driven and non-destructive

Migration will be a documented maintenance workflow rather than an automatic application startup action:

1. Run Garage alongside the existing MinIO data through a migration-only Compose override or the previous Compose revision.
2. Copy the `langfuse` bucket with an S3-capable migration tool such as `mc` or `rclone`, without deleting source objects.
3. Compare object inventories and content. Use downloaded-content verification or an equivalent tool mode instead of treating multipart ETags as universal MD5 hashes.
4. Quiesce Langfuse web and worker writes.
5. Run a final delta copy and repeat verification.
6. Start Langfuse against Garage and smoke-test trace/event ingestion plus media upload and download.
7. Preserve MinIO data for a defined rollback window.

The main Compose profile will contain no normal MinIO dependency after cutover. Migration-only assets may temporarily reference the old volume but must not remove or recreate it.

Alternatives considered:

- Start with an empty Garage bucket: rejected because historical Langfuse event/media objects would become unavailable.
- Automatically mirror at every startup: rejected because hidden copying can extend startup, race active writes, and make destructive operator mistakes harder to detect.
- Delete MinIO immediately after a successful smoke test: rejected because delayed failures may still require rollback.

### Treat Garage backup and durability separately from migration

The migration copy is not a backup strategy. Documentation will state that a single-node, replication-factor-one Garage deployment still has a single failure domain and that recoverability requires preserving both Garage metadata and object data using a consistent backup or snapshot process.

## Risks / Trade-offs

- [Risk] Garage is healthy at the process level but layout, bucket, or permission initialization is incomplete → Gate Langfuse startup on successful idempotent initialization and add an S3 operation smoke check.
- [Risk] The media endpoint signs URLs with a Compose-only hostname → Keep media endpoint separately configurable and test the generated URL from the host/browser side.
- [Risk] A reused MinIO environment variable silently configures the wrong credentials → Rename local variables to Garage-specific names and test that MinIO names are absent from normal profile configuration.
- [Risk] Garage S3 compatibility differs from MinIO for a Langfuse operation → Exercise event put/get/list behavior and presigned media upload/download against the pinned version before removing rollback data.
- [Risk] A partial copy loses historical objects → Use initial plus quiesced delta copies, compare inventories, and preserve source data.
- [Risk] Operators interpret single-node Garage as durable replication → Document replication factor one and the need for independent backup or a real multi-node topology.
- [Risk] Initializer scripts drift from Garage CLI syntax → Pin the image, keep initialization commands covered by a Compose smoke test, and update the pin deliberately.

## Migration Plan

1. Add pinned Garage configuration, metadata/data volumes, process health check, and idempotent initializer.
2. Configure Langfuse event access with the internal endpoint and media access with the host-reachable endpoint.
3. Add automated Compose-structure tests and a manual or automated profile smoke test covering initialization and S3 operations.
4. Document how to start old MinIO data concurrently with Garage for migration.
5. Perform an initial non-destructive bucket copy and integrity verification.
6. Stop Langfuse web and worker, run and verify a final delta copy, then start them against Garage.
7. Verify trace ingestion, event processing, media upload, and media download.
8. Keep the old MinIO volume unchanged through the configured rollback window, then retire it through a separate explicit operator action.

Rollback restores the prior Compose revision or migration override, points Langfuse event and media settings back to MinIO, and restarts Langfuse web and worker. Garage volumes remain untouched so the cutover can be investigated or retried.

## Open Questions

- Which exact Garage release will be pinned after implementation-time compatibility testing with the current Langfuse v3 images?
- What default logical capacity should the single local Garage node receive in its initialized layout?
- What rollback retention period should shared non-local deployments use? The repository documentation can recommend a minimum but cannot enforce an operator's backup policy.
