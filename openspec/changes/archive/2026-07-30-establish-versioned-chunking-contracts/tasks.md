## 1. Strategy and Tokenizer Contracts

- [x] 1.1 Add project-owned `ChunkingStrategy`, `ChunkingContext`, `ChunkSlice`, token-estimator, and revision value types with validation tests.
- [x] 1.2 Move current fixed-character behavior behind a compatibility strategy and prove existing fixture output is unchanged.
- [x] 1.3 Implement known-model `cl100k_base`, supported explicit tokenizer, unknown-ID rejection, and deterministic `utf8-byte-v1` fallback policies.
- [x] 1.4 Implement canonical effective-settings hashing and chunker revision calculation with order-independence tests.

## 2. Profiles, Settings, and Persistence

- [x] 2.1 Add nullable typed `tokenizerId` to AI profile domain, DTO, PostgreSQL migration, mapping, validation, and revision logic.
- [x] 2.2 Extend embedding-space compatibility checks to include resolved tokenizer identity without exposing profile secrets.
- [x] 2.3 Add typed strategy/target/overlap/hard-limit settings, compatibility-key precedence, lifecycle reporting, and atomic validation.
- [x] 2.4 Snapshot chunk strategy, settings hash, tokenizer identity, count mode, and effective revision on processing runs and new chunk metadata.

## 3. Baseline and Verification

- [x] 3.1 Add deterministic fixed-baseline fixtures for repeated text, multilingual UTF-8, boundaries, long identifiers, overlap, and ingestion cost counters.
- [x] 3.2 Update API documentation and contributor configuration guidance for tokenizer and versioned chunking settings.
- [x] 3.3 Run focused unit tests and the relevant Testcontainers processing/profile/settings integration tests.
- [x] 3.4 Run `graphify update .` after implementation.
