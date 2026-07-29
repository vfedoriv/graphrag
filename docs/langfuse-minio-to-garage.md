# Langfuse MinIO-to-Garage migration

This runbook migrates only Langfuse event and media objects. GraphRAG documents
and schema-draft files remain in the configured local filesystem storage.

The normal `langfuse` profile uses Garage `v2.3.0` at replication factor one.
That local topology has one failure domain: it is not highly available, and a
migration copy is not a backup. Before starting, take a consistent backup or
snapshot of the existing MinIO volume and plan consistent backups of both
`langfuse_garage_meta` and `langfuse_garage_data`.

## Prerequisites

- Docker Compose with support for `service_completed_successfully`.
- The old MinIO volume still present and a known-good MinIO image compatible
  with its on-disk format.
- Enough free space for both stores.
- The old MinIO and new Garage credentials.
- A maintenance window for the final delta and acceptance checks.

Never run `docker compose down -v`, remove the old MinIO volume, or use an
S3 command with delete/purge semantics during the rollback window.

Set the source volume and image explicitly when they differ from the former
local defaults:

```bash
export LANGFUSE_MINIO_VOLUME_NAME=graphrag_langfuse_minio_data
export LANGFUSE_MIGRATION_MINIO_IMAGE=minio/minio:<the-exact-previous-release>
export LANGFUSE_MIGRATION_MINIO_ACCESS_KEY=<old-access-key>
export LANGFUSE_MIGRATION_MINIO_SECRET_KEY=<old-secret-key>
```

Garage credentials, RPC/admin secrets, region, capacity, and the browser-facing
media endpoint can also be overridden with `LANGFUSE_GARAGE_*` variables. The
repository defaults are deterministic local-development values only.

## Start both object stores

The migration override mounts the existing MinIO volume by external name. It
does not create, recreate, or delete that source volume.

```bash
./scripts/migrate-langfuse-minio-to-garage.sh start
docker compose --profile langfuse ps -a langfuse-garage langfuse-garage-init
```

Garage must be healthy and `langfuse-garage-init` must have exited with status
zero. To inspect the initialized layout, bucket, and key:

```bash
docker compose --profile langfuse exec langfuse-garage /garage status
docker compose --profile langfuse run --rm langfuse-garage-init
```

The repeated initializer command must also exit zero without replacing the
existing bucket or key.

## Initial non-destructive copy and verification

The `copy` action uses `rclone copy`, which never deletes source or
destination-only objects. Do not substitute `sync`, `move`, `purge`, or
`--delete-*`.

```bash
./scripts/migrate-langfuse-minio-to-garage.sh copy
./scripts/migrate-langfuse-minio-to-garage.sh verify
```

Verification first compares sorted `path + size` inventories. It then runs
`rclone check --download`, which reads and compares the bytes from both stores.
This is deliberate: an S3 multipart ETag is not a universal MD5 content hash.
Keep the successful command output with the maintenance record.

## Quiesce, final delta, and cutover

1. Stop all Langfuse web/worker instances and any direct event/media writers.
2. Confirm no ingestion or media requests are still in flight.
3. Run the same non-destructive copy and verification again:

   ```bash
   docker compose --profile langfuse stop langfuse-web langfuse-worker
   ./scripts/migrate-langfuse-minio-to-garage.sh copy
   ./scripts/migrate-langfuse-minio-to-garage.sh verify
   ```

4. Start the normal Garage-backed profile:

   ```bash
   docker compose --profile langfuse up -d
   ```

5. Run the Garage smoke test and the Langfuse acceptance checks documented in
   the README. Confirm a new trace/event is ingested, then upload and download a
   media object through the browser-facing presigned URL. The URL host must be
   reachable by the browser (default `http://localhost:9090`), not the internal
   Compose hostname.

## Rollback

If any acceptance check fails, stop Langfuse and restart it with the migration
and rollback overrides. This points event traffic to the internal migration
MinIO endpoint and media presigned URLs to `http://localhost:9190` by default:

```bash
docker compose --profile langfuse stop langfuse-web langfuse-worker
docker compose \
  -f compose.yaml \
  -f compose.langfuse-migration.yaml \
  -f compose.langfuse-minio-rollback.yaml \
  --profile langfuse \
  --profile langfuse-migration \
  up -d langfuse-minio-migration langfuse-web langfuse-worker
```

Repeat trace/event and media upload/download checks against MinIO. The source
volume must remain preserved; Garage volumes should also remain untouched for
diagnosis and a later retry.

## Delayed retirement

Choose and record a rollback retention period appropriate to the deployment.
During it, keep the MinIO volume, its exact image version, credentials, backup,
and the rollback commands above. Stop the migration-only server when it is not
needed:

```bash
./scripts/migrate-langfuse-minio-to-garage.sh stop-source
```

Retire MinIO only through a separate, explicit operator change after the
rollback window closes and Garage backups have been restored successfully in a
test environment. This repository intentionally provides no command that
deletes the old MinIO volume.
