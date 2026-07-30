## ADDED Requirements

### Requirement: Chunking policy snapshot
Each document processing run SHALL snapshot the effective strategy name/revision, canonical chunk-settings hash, tokenizer or estimator identity, exact-versus-conservative count mode, and effective chunker revision used by that attempt.

#### Scenario: Processing starts
- **WHEN** a processing run begins
- **THEN** its chunking snapshot is fixed for the attempt and remains queryable after success or failure

#### Scenario: Runtime setting changes during processing
- **WHEN** a live chunking setting changes after the run snapshot is created
- **THEN** the in-flight run continues with its snapshot rather than mixing revisions
