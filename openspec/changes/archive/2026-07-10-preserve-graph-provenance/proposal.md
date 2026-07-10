## Why

The current graph writer merges a semantic relationship across documents and then stores a single document and extraction-run identifier on that shared relationship. A later extraction can overwrite earlier provenance, causing replacement, retry, or deletion cleanup to remove a fact that remains supported by another document.

## What Changes

- Preserve immutable extraction evidence per document, chunk, and run independently from a canonical semantic graph fact.
- Make cleanup remove only the target run's or document's evidence and remove a canonical fact only after its last evidence record is gone.
- Migrate existing single-valued graph provenance safely and expose evidence-aware cleanup counts.
- Add cross-document, retry, overwrite, and deletion coverage for shared nodes and relationships.

## Capabilities

### New Capabilities
- `multi-source-graph-provenance`: represents and retains independent evidence for a graph fact asserted by multiple documents or extraction runs.

### Modified Capabilities
- `graph-identity-persistence`: distinguish canonical fact identity from the identity of an individual extraction assertion.
- `graph-artifact-cleanup`: remove document-scoped evidence without deleting facts that retained evidence supports.
- `extraction-run-cleanup`: remove stale-run evidence while retaining shared graph facts and evidence from retained runs.

## Impact

Affected graph writes, extraction-run cleanup, document replacement/deletion cleanup, Neo4j data migration, graph traversal queries, and integration tests. Existing persisted extracted relationships require a migration path.
