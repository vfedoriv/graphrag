## 1. Topology invariant and audit

- [x] 1.1 Add a document-scoped topology classifier that distinguishes `EMPTY`, `FLAT`, `HIERARCHICAL`, and invalid populations from parent, parented-child, unparented-child, orphan, revision, and unsupported-kind facts.
- [x] 1.2 Refactor chunk replacement validation to accept pure-flat and valid hierarchy batches, reject invalid or mixed batches before `deleteByDocumentId`, and preserve same-scope parent/child checks.
- [x] 1.3 Add a read-only Neo4j topology audit with per-document counts and document the zero-invalid-row deployment precondition without adding automatic cleanup.

## 2. Bounded read behavior

- [x] 2.1 Apply topology classification to owned bounded chunk-page and hierarchy-summary reads before returning collection content.
- [x] 2.2 Map invalid persisted topology to RFC 7807 `409 Conflict` with detail `Document chunk topology is invalid`, while retaining existing ownership-safe `404` and input-validation `400` behavior.
- [x] 2.3 Preserve pure-flat `kind=FLAT` paging, returned `kind=CHILD`, section filtering, deterministic totals/order, and pure-hierarchy `flatChunkCount=0`.

## 3. Regression coverage

- [x] 3.1 Add persistence-adapter tests proving mixed input is rejected before destructive replacement and valid fixed-character flat input remains accepted.
- [x] 3.2 Replace mixed-success Neo4j fixtures with separate pure-flat and pure-hierarchy fixtures plus explicit invalid-topology rejection coverage.
- [x] 3.3 Update service, controller, and OpenAPI contract tests for stable `409` behavior and unchanged valid response schemas.
- [x] 3.4 Cover overwrite transitions from flat to hierarchy and hierarchy to flat without stale chunks, relationships, indexes, evidence, or revisions.

## 4. Verification and rollout

- [x] 4.1 Run the topology audit against the target database and record zero invalid documents before deployment.
- [x] 4.2 Run the backend unit, integration, architecture, formatting, and full build quality gates required by the repository.
- [x] 4.3 Live-smoke one recursive document and one fixed-character document through bounded hierarchy, FLAT page, and direct lookup routes.
