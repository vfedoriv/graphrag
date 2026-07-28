## Context

After document and run metadata move to PostgreSQL, Neo4j must answer graph-native questions without traversing operational `KnowledgeBase`, `DocumentUpload`, or `ExtractionRun` anchors. Chunks and evidence therefore need copied authoritative identifiers. Search also needs relational enrichment after vector retrieval.

## Goals / Non-Goals

**Goals:**

- Restrict Neo4j to chunks, embeddings, facts, evidence, provenance, and graph-native relationships.
- Preserve KB isolation, embedding compatibility, hybrid-search recall, and cleanup correctness.
- Remove graph dependence on operational root nodes.

**Non-Goals:**

- Moving vector search or extracted facts to PostgreSQL.
- Changing query-tenancy semantics beyond current KB scoping.
- Removing every legacy initializer before the final cutover verification.

## Decisions

1. **Copy stable scope identifiers onto graph artifacts.** Every `DocumentChunk` and `GraphExtractionEvidence` carries `knowledgeBaseId`; chunks retain `documentId`; evidence retains `sourceDocumentId` and `extractionRunId`. PostgreSQL remains authoritative for these IDs.

2. **Anchor provenance in graph artifacts, not runs.** Evidence is created directly and linked to chunks/facts as appropriate. No `MATCH` or `MERGE` of an operational extraction-run node is required.

3. **Scope vector retrieval before limiting candidates.** Hybrid search selects the KB/embedding-space index and filters on chunk scope within Neo4j. It does not fetch global candidates and post-filter them. Returned document IDs are deduplicated and batch-loaded from PostgreSQL to avoid N+1 enrichment.

4. **Clean by copied IDs and evidence reachability.** Failed/stale run IDs come from PostgreSQL. Evidence is deleted by run/document scope; chunks are deleted by document scope. Candidate facts are deleted only after verifying that no evidence remains.

5. **Keep `Neo4jClient` for graph-native work.** Dynamic schema-constrained Cypher, projections, vector queries, graph writes, and cleanup remain client-based. SDN repositories are limited to simple chunk persistence to avoid modeling dynamic facts as fixed entities.

6. **Replace indexes deliberately.** New constraints and indexes cover chunk identity, evidence identity, KB/document/run scope, provenance, and vector spaces. Legacy operational schema objects are retired only after their callers are gone.

## Risks / Trade-offs

- [Copied scope drifts from PostgreSQL] → Require explicit scope inputs at write boundaries and test mismatched/missing scope rejection.
- [Batch enrichment returns missing documents] → Drop or report stale hits deterministically and expose metadata-only diagnostics without content previews.
- [Cleanup deletes a shared fact] → Delete facts only after an evidence-existence check scoped to the candidate IDs.
- [Index selection leaks another KB's candidates] → Test per-KB recall with competing vectors and enforce scoped index lifecycle.

## Migration Plan

1. Extend chunk/evidence write contracts and Neo4j indexes.
2. Update processing and extraction to pass explicit KB/document/run scope.
3. Rewrite hybrid search and embedding compatibility lookup, including PostgreSQL batch enrichment.
4. Rewrite run/document cleanup and provenance creation without operational anchors.
5. Add isolation, retry, overwrite, and graph-purity integration tests.
6. Leave broad legacy label removal to the final cutover after all domains migrate.

Rollback requires resetting disposable GraphRAG Neo4j data because graph artifacts written without operational anchors are not backward-compatible with the old traversal model.

## Open Questions

- None.
