## Context

Document chunks retain model and dimension metadata, but vector-index creation uses one global index name and the first observed dimension. AI profile reassignment, profile update, and hybrid search each implement different compatibility checks. A knowledge base can therefore accept or use an embedding space that is incompatible with its stored vectors.

## Goals / Non-Goals

**Goals:**
- Define one stable, non-secret identity for an embedding vector space.
- Ensure vector writes and retrieval use an index isolated to one knowledge base and embedding space.
- Apply compatibility checks consistently before profile or embedding state changes.
- Backfill legacy chunks without silently mixing vector spaces.

**Non-Goals:**
- Re-embed all existing chunks automatically.
- Expose raw provider credentials or embedding vectors through new APIs.
- Change chat-model selection behavior.

## Decisions

### Derive a non-secret embedding-space identity

An embedding-space ID is derived from normalized provider base URL, embedding model, and dimensions, excluding API keys and mutable timeout/retry settings. Chunks persist this ID. This distinguishes embeddings that share dimensions but originate from semantically different providers or models.

### Use deterministic KB-and-space vector indexes

Each populated `(knowledgeBaseId, embeddingSpaceId)` pair receives a deterministic, safely hashed Neo4j label and vector-index name. Search queries the matching index directly, so global candidates from other KBs cannot displace correct results. The alternative of one global index plus post-query filtering is rejected because candidate limits cannot guarantee per-KB recall.

### Centralize compatibility policy

One `EmbeddingSpacePolicy` compares profile configuration, persisted chunks, and index metadata. Profile assignment, profile update, document processing, and hybrid search call it. Profile updates are rejected atomically if any assigned KB contains incompatible chunks.

## Risks / Trade-offs

- [More vector indexes] → Create lazily, use stable hashes, report index counts, and define cleanup for empty KB-space pairs.
- [Legacy chunks lack provider endpoint metadata] → Assign only when their KB's active profile unambiguously matches model and dimensions; otherwise mark the KB for explicit re-embedding.
- [Profile shared by many KBs] → Validate all assignments before persisting updates and return affected KB identifiers in conflicts.
- [Neo4j schema-token growth] → Enforce a bounded, observable index lifecycle and review tenant/profile scale before rollout.

## Migration Plan

1. Add embedding-space metadata and policy in compatibility-only mode.
2. Backfill unambiguous legacy chunks and create their isolated vector indexes.
3. Verify index dimensions and chunk counts, then switch processing and hybrid search to space-specific indexes.
4. Reject ambiguous legacy spaces until explicitly re-embedded; retain the global index only until no legacy reads remain.

## Open Questions

- What upper bound on active KB-and-space indexes is acceptable for the supported Neo4j deployment?
- Should empty indexes be removed immediately or during scheduled maintenance?
