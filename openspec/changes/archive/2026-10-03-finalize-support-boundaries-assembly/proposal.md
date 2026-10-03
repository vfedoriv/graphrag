## Why

Roadmap steps 1–8 established document, schema, search, knowledge-base, and AI state boundaries, but exact step-9 exceptions still allow dependencies on legacy support implementations and application configuration. Closing those seams completes the feature graph and prevents settings, observability, or shared infrastructure from becoming routes back into feature internals.

## What Changes

- Consolidate settings catalog, codecs, validation, lifecycle, typed access, and relational persistence under settings ownership; move feature-specific query-policy and chunker-revision composition to their owners.
- Finish AI profile/model/context and knowledge-base ownership, replacing foreign mutable profile records and implementation-service access with purpose-specific public capabilities and immutable non-secret facts.
- Place tokenizer identity/compatibility policy under AI ownership while retaining chunking algorithms under documents.
- Establish shared vector/full-text index support contracts and infrastructure adapters consumed by documents and search without dependencies between their implementations.
- Govern tracing, metadata-only logging, shared API values/errors, storage, and store-qualified transaction support; move search-specific metric interpretation into search.
- Consolidate application configuration, startup, persistence scanning, and integration assembly under bootstrap, keeping mapping adapters transaction-free and policy-free.
- Retire every recorded roadmap step-9 exception and enforce permanent owner, public-contract, support, adapter, and assembly rules with negative fixtures.
- Align the architecture portal, roadmap, and contributor guidance with the completed migration.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `architecture-boundary-governance`: Enforce final AI/knowledge-base/support ownership, feature-independent settings and observability, governed shared index support, one-way assembly dependencies, and complete retirement of roadmap step-9 exceptions.

## Impact

Affected code includes legacy controller/DTO/domain/service/repository/config packages, AI and knowledge-base capabilities/adapters, settings infrastructure, tokenizer values, embedding/full-text index support, observability, common HTTP support, transaction support, binary storage wiring, bootstrap, architecture tests, and affected behavioral/startup/persistence tests.

This is an ownership migration in one application. HTTP paths, JSON/problem contracts, SQL mappings, serialized historical snapshots, fingerprints, profile revisions/model caches, metrics, index identities, configuration precedence, transaction participation, and recovery behavior remain compatible. No SQL/data migration, new provider behavior, retrieval/chunking algorithm change, dependency upgrade, build-module split, or remote worker is planned. Pre-existing transactional self-invocation exceptions are assessed individually rather than assumed to be step-9 behavior changes.
