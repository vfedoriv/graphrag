# Backend Improvements for Chunking and Advanced Search

## Summary

The frontend can support the current backend contracts, but several backend-shaped constraints limit UX quality, weaken contract discoverability, or force inefficient client-side workarounds.

These recommendations should be handled as a separate backend change. They are not required to begin the frontend implementation, and they should not be implemented by changing backend contracts from the frontend repository.

## Findings and Recommendations

### 1. Advanced-search history lacks the submitted query and applied options

**Priority:** High

`AdvancedSearchRunDtos.RunResponse` does not expose the submitted query, requested maximum evidence, or evidence-text preference.

Consequences:

- After a reload, the frontend can identify a run only by ID, status, stage, and timestamps.
- Search history cannot show users what question each run represents.
- The UI cannot explain which evidence options were snapshotted for a historical run.

Recommendation:

- Add owner-scoped `query`, `maximumEvidence`, and `includeEvidenceText` fields to run responses.
- If returning the complete query is undesirable, expose a bounded `queryPreview` while keeping the full query available on the owned run-detail resource.
- Continue excluding queries from operational logs; API visibility to an authorized owner is a separate concern from logging policy.

### 2. Advanced-search results use an untyped `JsonNode` contract

**Priority:** High

`ResultResponse` exposes its versioned result as a generic JSON node even though the payload has a defined answer, claim, evidence, graph-fact, and diagnostics structure.

Consequences:

- The frontend must validate payload version 1 at runtime.
- Contract drift can only be handled through a raw-JSON fallback.
- Generated OpenAPI documentation cannot accurately describe the result.
- Other clients must independently reverse-engineer the payload.

Recommendation:

- Introduce explicit versioned DTOs for answers, confidence, limitations, claims, source ranges, evidence, graph facts, answer diagnostics, and retrieval/ranking diagnostics.
- Keep `payloadVersion` as the evolution boundary.
- Define nullable and optional fields explicitly so partial results remain representable.
- Add serialization and OpenAPI schema tests using completed, partial, insufficient-evidence, and answer-unavailable fixtures.

### 3. Search evidence is not a self-contained historical citation

**Priority:** High

Evidence contains `documentId` and chunk provenance but no durable human-readable source name.

Consequences:

- The frontend must join evidence against the current document list to display a filename.
- Historical results become less understandable if the source document is renamed, replaced, or unavailable.
- A durable search result is not independently useful as an audit record.

Recommendation:

- Snapshot citation-safe source metadata into each evidence entry, including original filename and content type.
- Consider also including a stable source display label when different document types need richer naming.
- Treat these values as immutable citation metadata belonging to the run snapshot.

### 4. Advanced-search submission does not fail fast on missing readiness

**Priority:** High

Run creation snapshots the active AI profile ID but does not reject admission when the selected knowledge base lacks a usable profile or other required retrieval state.

Consequences:

- The backend can accept a durable run that is already unable to complete successfully.
- The user sees a later processing failure instead of an immediate actionable admission error.
- Queue capacity and persisted run history can be consumed by invalid work.

Recommendation:

- Validate active AI profile and embedding readiness before persisting a run.
- Return a specific RFC 7807 `409 Conflict` with a stable problem type or category for each readiness blocker.
- Decide explicitly whether an active schema is optional, required, or only disables the graph branch.
- Optionally expose a read-only advanced-search readiness endpoint so clients can explain availability before submission.

### 5. Reprocessing history cannot be filtered by plan reason

**Priority:** High

`GET /api/v1/knowledge-bases/{knowledgeBaseId}/reprocessing-plans` supports `draftId` filtering but not `reason`, `selection`, or status.

Consequences:

- Schema-activation and chunk-migration plans share one server-paginated history.
- A dedicated Chunking page cannot provide complete migration-only history.
- Client-side filtering would produce sparse pages and totals that refer to a different result set.

Recommendation:

- Add optional `reason`, `selection`, and `status` query parameters.
- Apply filters in the repository query before pagination and total calculation.
- Preserve the unfiltered behavior for existing clients.
- Add ownership, combined-filter, ordering, and paging tests.

### 6. Chunk migration has no readiness or preview resource

**Priority:** High

The migration creation endpoint can select outdated documents, but there is no read-only operation that reports what would be selected.

Consequences:

- Before confirmation, the frontend cannot show how many documents are outdated.
- Active-schema, active-profile, revision, and other blockers must be inferred from multiple resources.
- Users only discover the selected document count after creating durable work.

Recommendation:

- Add a knowledge-base-scoped chunk-migration readiness or preview endpoint.
- Return:
  - Current effective chunker revision and migration lifecycle.
  - Active schema, AI profile, and embedding-space readiness.
  - Stable blocker codes and readable descriptions.
  - Counts for documents with no chunks, outdated revisions, and current revisions.
  - A bounded or paginated document preview for the requested selection.
- Accept `OUTDATED_STRATEGY`, `DOCUMENT_IDS`, and `ALL` preview inputs without creating a plan.
- Bind plan creation to the returned revision through the existing `expectedChunkerRevision` guard.

### 7. Document chunk reads are unpaged and have no direct lookup

**Priority:** Medium

`GET /api/v1/documents/{documentId}/chunks` returns the complete parent-child hierarchy for a document.

Consequences:

- Large documents require downloading and rendering every chunk.
- A citation deep link must fetch the complete collection to locate one chunk.
- The client cannot incrementally load parents or children.

Recommendation:

- Add `GET /api/v1/documents/{documentId}/chunks/{chunkId}` for direct owned lookup.
- Add server-side paging and filters such as `kind`, `parentChunkId`, section, and page.
- Consider a hierarchy-summary response that lists parents and child counts without returning all child text.
- Preserve deterministic document/hierarchy ordering.
- Keep the existing collection route temporarily for backward compatibility.

### 8. Effective chunking state is duplicated across runtime settings

**Priority:** Medium

Every runtime-setting response repeats `effectiveChunkerRevision` and `chunkMigrationLifecycle`, although these describe one global chunking state.

Consequences:

- Clients must derive one authoritative value from repeated metadata.
- A chunking-oriented UI must understand generic runtime-setting details to build a strategy summary.
- Migration prerequisites and canonical-versus-compatibility settings remain fragmented.

Recommendation:

- Add a dedicated read-only chunking-state resource containing:
  - Canonical effective settings.
  - Strategy, tokenizer, parser-policy, representation, and effective revisions.
  - Migration lifecycle description.
  - Compatibility aliases and their precedence without presenting them as independent effective controls.
- Keep runtime-settings endpoints as the mutation mechanism.
- Return the refreshed chunking-state representation after a successful bulk chunk-setting update where practical.

### 9. The retry request exposes an unsupported false mode

**Priority:** Medium

`RetryPlanRequest` contains `resnapshotUnresolvedDocuments`, but the service rejects `false`.

Consequences:

- The wire contract appears to support two behaviors although only one is valid.
- Clients can submit structurally valid requests that are guaranteed to fail.
- Existing UI wording can incorrectly imply that retry without resnapshot is supported.

Recommendation:

- Make resnapshot explicitly required through validation, replace the boolean with a closed retry-mode enum, or remove the request body if retry always means resnapshot.
- Prefer a closed enum if more retry policies are expected later.
- Document the immutable prior successes and newly snapshotted unresolved targets in OpenAPI examples.

### 10. Hybrid Search removal created an abrupt client break

**Priority:** Medium

The legacy hybrid-search endpoint and DTOs were removed while the frontend still contained a Hybrid Search tab.

Consequences:

- Existing clients call an endpoint that no longer exists.
- The replacement is a different asynchronous lifecycle rather than a drop-in response shape.

Recommendation:

- For future endpoint retirements, mark the old endpoint deprecated for a defined compatibility window.
- Return or document a replacement relation pointing to advanced-search runs.
- Publish the lifecycle and payload differences in migration notes.
- Add a contract check that compares frontend-consumed endpoints against generated backend OpenAPI where feasible.

### 11. The documented advanced-search request field is inconsistent

**Priority:** Low

The controller example uses `maxEvidence`, while the Java request property is `maximumEvidence`.

Consequences:

- Consumers copying the example submit the wrong field.
- The backend silently applies the default when unknown properties are tolerated, making the mistake difficult to notice.

Recommendation:

- Correct examples to use `maximumEvidence`.
- Add controller serialization tests for documented request examples.
- Consider rejecting unknown request properties consistently if that matches the broader API policy.

### 12. Answer text has no inline citation structure

**Priority:** Low

Claims reference citation IDs, but the synthesized answer text does not identify which passage corresponds to which claim.

Consequences:

- The frontend can display cited claims below the answer but cannot safely attach citations to exact answer passages.
- Attempting to infer positions from repeated claim text would be brittle.

Recommendation:

- Consider an ordered `answerSegments` representation containing text plus claim and citation IDs.
- Preserve the current claims collection for validation and auditability.
- Validate that concatenated display segments remain consistent with the canonical answer text, or make segments the canonical presentation contract.

## Recommended Delivery Sequence

### Phase 1: Contract correctness

- Correct the `maximumEvidence` OpenAPI example.
- Align retry request validation with its actual supported behavior.
- Add fail-fast advanced-search admission checks and stable problem categories.

### Phase 2: Self-describing advanced-search resources

- Add query and applied request options to run responses.
- Replace the public generic result node with versioned typed DTOs.
- Snapshot human-readable source metadata into evidence.

### Phase 3: Reprocessing usability

- Add server-side plan filtering by reason, selection, and status.
- Add chunk-migration readiness and preview.
- Introduce a dedicated effective chunking-state representation.

### Phase 4: Scalable chunk inspection

- Add direct chunk lookup.
- Add paginated and hierarchy-aware chunk reads.
- Retain compatibility for the existing complete-list endpoint during migration.

### Phase 5: Richer answer presentation

- Evaluate ordered answer segments or another explicit inline citation contract after the core frontend and result DTOs are stable.

## Compatibility and Rollout

- Make additive response changes backward-compatible wherever possible.
- Preserve existing endpoints while introducing filtered, preview, or direct-lookup variants.
- Version the advanced-search result payload independently of the REST API path.
- Update backend OpenSpec artifacts and generated OpenAPI before frontend clients depend on new fields.
- Cover old and new response readers during the compatibility window.
- Keep privacy-safe logging rules unchanged: user query and evidence visibility in owned API resources must not cause those values to appear in operational logs.
