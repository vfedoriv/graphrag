## Why

After operational document state moves to PostgreSQL, Neo4j queries can no longer rely on operational root nodes or relationships for knowledge-base ownership, run status, or document enrichment. Graph artifacts need direct, authoritative scope identifiers and cleanup rules so Neo4j contains only graph-native data.

## What Changes

- Require PostgreSQL-owned knowledge bases, schemas, documents, and run history.
- Add direct knowledge-base scope to every chunk and evidence record and pass that scope explicitly through processing and extraction.
- Create evidence without matching operational extraction-run nodes while retaining evidence-to-chunk, evidence-to-fact, and provenance relationships.
- Filter vector and hybrid search directly by chunk scope, then batch-enrich hits with PostgreSQL document metadata.
- Resolve failed or stale runs from PostgreSQL and clean evidence by stable run ID, document ID, and knowledge-base ID.
- Delete extracted facts only when no evidence remains and replace legacy operational constraints with graph-data, scope, provenance, and vector indexes.
- Restrict SDN repositories to straightforward graph persistence while retaining `Neo4jClient` for dynamic Cypher, vector search, projections, writes, and cleanup.

## Capabilities

### New Capabilities

- `graph-data-plane-isolation`: Define the graph-only ownership, scope, index, and operational-anchor exclusion contract for Neo4j.

### Modified Capabilities

- `hybrid-search`: Scope graph candidates directly and enrich document metadata from PostgreSQL in bounded batches.
- `embedding-space-management`: Manage vector indexes and compatibility without operational KB nodes in Neo4j.
- `multi-source-graph-provenance`: Preserve evidence and source provenance without document or extraction-run anchors.
- `graph-artifact-cleanup`: Clean graph artifacts by copied authoritative identifiers and evidence reachability.
- `application-workflow-orchestration`: Coordinate relational run state with idempotent graph writes and cleanup.

## Impact

- Affects chunk/evidence models, processing, extraction, graph write and cleanup services, hybrid search, embedding index management, Neo4j migrations/index initialization, relational metadata enrichment, and end-to-end graph assertions.
- Does not move extracted facts, evidence, chunks, embeddings, or graph-native relationships out of Neo4j.
