# Glossary

**Active schema** — The one registered schema version currently constraining extraction and Cypher for a knowledge base.

**Aggregate revision** — A deterministic schema-draft analysis projection over a captured source/guidance/profile/settings snapshot.

**AI profile** — Revisioned, PostgreSQL-backed OpenAI-compatible chat/embedding configuration with a write-only API key.

**Canonical fact** — A deduplicated schema-defined Neo4j node or relationship supported by one or more evidence observations.

**Child chunk** — A retrieval chunk that may have an embedding and parent context. A child with no parent is exposed by the virtual `FLAT` filter.

**Content drift** — A published schema's current inactive content hash differs from the exact hash recorded at draft publication.

**Embedding space** — Provider/base URL, embedding model, dimensions, and resolved tokenizer identity that must remain compatible with stored chunks.

**Evidence** — A cited or extracted observation tied to document/chunk/source-range and reproducibility metadata.

**Effective chunker revision** — Hash/revision identity derived from strategy, settings, tokenizer, parser, and representation behavior for migration comparison.

**Knowledge base (KB)** — Client-named ownership scope for profile, schema, documents, graph retrieval, and queries.

**Operational state** — Lifecycle/ownership records in PostgreSQL, as distinct from retrieval and graph facts in Neo4j.

**Parent chunk** — Non-retrieval context grouping for bounded child chunks; returned as context/structure rather than a direct cited hit.

**Partial result** — A terminal durable result that retains useful output while identifying optional branch/stage degradation.

**Profile-managed setting** — Provider behavior visible in settings metadata but changed through AI profile APIs.

**Projection content hash** — Canonical hash that binds schema-draft publication readiness and publish request to one exact projection/revision.

**Provenance** — Trace from fact/citation through evidence and chunks to document, source offsets, processing run, schema, profile, and settings revisions.

**Reprocessing plan** — Durable, bounded, itemized overwrite workflow after schema activation or chunking migration.

**Runtime override** — Validated PostgreSQL-backed desired setting that applies live or after restart according to catalog metadata.

**Source hash** — SHA-256 identity used for document deduplication and stale/reuse decisions.

**Tokenizer identity** — Explicit or resolved token-count policy (`cl100k_base` exact or versioned conservative fallback) included in compatibility and chunk snapshots.
