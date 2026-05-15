## Why

Extraction-run cleanup deleted old `ExtractionRun` nodes and their direct relationships, but it did not remove graph relationships whose provenance was stored only in `extractionRunId` properties. This allowed stale extracted subgraphs, including `Person` nodes and related domain nodes, to remain connected after the run that produced them was deleted.

## What Changes

- Delete domain graph relationships for deleted extraction runs by matching `sourceDocumentId` and `extractionRunId` on relationships.
- After deleting stale run-scoped relationships, delete extracted nodes for the same document that no longer have any retained `CREATED_NODE` provenance from an existing extraction run.
- Preserve nodes that are still created by retained extraction runs.
- Extend integration coverage to prove that old-only related nodes and relationships are removed, not only standalone nodes.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `extraction-run-cleanup`: clarify that cleanup removes stale extracted graph relationships and extracted nodes without retained run provenance, even when those nodes are still connected to other stale extracted nodes.

## Impact

- Affected service: `GraphExtractionService` cleanup query.
- Affected repository: removal of duplicated stale cleanup query from `ExtractionRunRepository`.
- Affected tests: `GraphExtractionCleanupIntegrationTest` now covers stale related node and relationship removal.
- Affected data behavior: future successful cleanup runs remove obsolete extracted subgraphs more completely.
