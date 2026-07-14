## Why

Stateless multi-source discovery cannot preserve evidence, user decisions, or incremental progress when representative documents arrive over time. A durable schema-draft lifecycle is needed so users can evolve a proposed schema without regenerating accepted work or changing the active extraction contract.

## What Changes

- Add knowledge-base-owned schema drafts with a target name/version, optional base schema, structured guidance, revision metadata, and lifecycle status.
- Add draft sources for knowledge-base document references, draft-owned multipart binaries, and pasted text, with content fingerprints and explicit ownership, retention, and cleanup rules.
- Add durable source-analysis runs with `RUNNING`, `COMPLETED`, `PARTIAL`, and `FAILED` outcomes, per-source retry, polling, and idempotent reuse keyed by source content, guidance revision, model profile revision, and prompt revision.
- Persist typed candidates, evidence references, conflicts, warnings, and deterministic aggregate revisions.
- Model evidence origins independently from review state so a candidate can be observed, guided, inferred, or inherited while separately being pending, accepted, rejected, modified, or pinned.
- Add incremental diffs against the optional base schema and the previously reviewed aggregate, including additive, review-required, and breaking classifications.
- Preserve accepted edits, pinned definitions, and rejected suggestions when sources are added or reanalyzed.
- Keep drafts separate from registered schemas and prevent drafts from becoming active extraction contracts.

## Capabilities

### New Capabilities
- `schema-draft-lifecycle`: Knowledge-base ownership, base-schema rules, guidance revisions, draft lifecycle, concurrency control, and deletion behavior.
- `schema-draft-sources`: Existing-document, draft-binary, and pasted-text source management with fingerprints, retention, and cleanup.
- `schema-draft-analysis`: Durable resumable analysis runs, idempotent source-result reuse, retry, per-source outcomes, and aggregate revisions.
- `schema-draft-review`: Candidate evidence origins, user decision states, pinned or modified definitions, conflict resolution, and incremental schema diffs.

### Modified Capabilities

None.

## Impact

- Depends on the candidate contracts and deterministic aggregation introduced by `add-multi-source-schema-discovery`.
- Adds Neo4j domain nodes, relationships, repositories, optimistic versioning, and transaction-aware lifecycle services for drafts and analysis metadata.
- Adds draft-owned binary storage and cleanup behavior without converting draft files into normal `DocumentUpload` records.
- Introduces a bounded background analysis executor and polling APIs; no general-purpose external job platform is required initially.
- Adds new schema-draft controllers and DTOs plus repository, concurrency, retry, cleanup, and Testcontainers coverage.

