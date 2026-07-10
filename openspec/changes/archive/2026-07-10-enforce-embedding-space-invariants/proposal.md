## Why

The application permits profile-specific embedding models and dimensions but creates one global vector index. It validates profile reassignment only in one path, while profile mutation and hybrid search duplicate incompatible checks; this can make stored embeddings unreadable or vector retrieval use the wrong space.

## What Changes

- Introduce an explicit embedding-space identity derived from the embedding provider endpoint, model, and dimensions.
- Persist the embedding-space identity on chunks and use vector retrieval isolated to the target knowledge base and embedding space.
- Apply one compatibility policy to profile assignment, profile mutation, processing, and hybrid search.
- Migrate existing chunks to a validated legacy embedding space and reject unsafe profile changes before state is altered.

## Capabilities

### New Capabilities
- `embedding-space-management`: manages embedding-space identity, vector-index lifecycle, and legacy chunk migration.

### Modified Capabilities
- `ai-profile-management`: prevent a profile update from invalidating embeddings in any assigned knowledge base.
- `hybrid-search`: retrieve from the requested knowledge base's compatible embedding space with bounded, correct graph expansion.

## Impact

Affected AI profile CRUD and assignment, document processing, chunk persistence, vector-index creation, hybrid search, Neo4j migrations, and integration tests.
