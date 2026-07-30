## Why

Chunking currently treats token overlap as characters, ignores the configured token maximum, and records no strategy or tokenizer revision. Before changing production boundaries, the system needs explicit, versioned contracts that make chunking deterministic, profile-aware, observable, and measurable while preserving the existing fixed strategy as a compatibility baseline.

## What Changes

- Introduce a project-owned chunking strategy boundary with typed context and source-position-aware slice results.
- Add a versioned token-counting policy that maps known OpenAI embedding models to `cl100k_base`, accepts supported explicit profile tokenizer identifiers, and uses `utf8-byte-v1` as a conservative fallback.
- Add typed `tokenizerId` AI-profile metadata and include it in profile revision and embedding-space compatibility decisions.
- Replace ambiguous effective chunking settings with validated strategy, target-token, overlap-token, and hard character-limit concepts while retaining compatibility aliases during this change.
- Snapshot chunker, tokenizer/estimator, and effective settings revisions on processing runs and persisted chunks.
- Add deterministic evaluation fixtures that capture the current fixed-character baseline; do not change the default production strategy in this change.

## Capabilities

### New Capabilities

- `versioned-chunking-strategy`: Project-owned strategy, slice, tokenizer-policy, revision, and baseline-evaluation contracts.

### Modified Capabilities

- `ai-profile-management`: Adds typed, write-safe tokenizer selection and revision semantics to AI profiles.
- `runtime-application-settings`: Defines validated and lifecycle-aware chunking strategy settings and reports their effective revision.
- `document-processing-run-history`: Snapshots the effective chunking and tokenizer policy used by each processing attempt.
- `embedding-space-management`: Includes tokenizer identity in embedding-space compatibility and profile-change safeguards.

## Impact

This affects chunking domain contracts, AI profile DTOs and PostgreSQL persistence, runtime settings, processing-run metadata, chunk metadata, configuration defaults, and deterministic tests. LangChain4j is the preferred implementation library behind the project-owned boundary; this change does not adopt recursive splitting or change existing chunk output.
