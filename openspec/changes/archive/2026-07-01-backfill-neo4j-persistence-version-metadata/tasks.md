## 1. Persistence Compatibility

- [x] 1.1 Add an idempotent startup backfill for assigned-id Neo4j application nodes missing SDN persistence version metadata.
- [x] 1.2 Backfill `SchemaDefinition` persistence metadata using `entityVersion` so schema business `version` remains unchanged.
- [x] 1.3 Keep the backfill limited to missing metadata and preserve existing business values, identifiers, labels, and relationships.

## 2. Verification

- [x] 2.1 Add a Neo4j-backed regression test for updating an existing AI profile that lacks persistence version metadata.
- [x] 2.2 Run focused AI profile, runtime settings, and knowledge-base integration tests.
- [x] 2.3 Refresh the code graph after implementation.
