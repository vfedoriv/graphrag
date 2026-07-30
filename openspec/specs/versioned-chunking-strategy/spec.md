# versioned-chunking-strategy Specification

## Purpose

Define stable, project-owned chunking and token-counting contracts with reproducible revisions and a compatibility baseline.

## Requirements

### Requirement: Project-owned chunking contract
The system SHALL execute document splitting through a project-owned strategy contract that accepts a parsed section plus typed effective context and returns ordered slices carrying exact source text, source positions when reliable, token count, strategy revision, tokenizer identity, and bounded diagnostics.

#### Scenario: Strategy output is library-independent
- **WHEN** a configured strategy splits a parsed section
- **THEN** downstream processing receives project-owned slice values and no external splitter type becomes part of the application or persistence contract

#### Scenario: Repeated source text remains unambiguous
- **WHEN** identical text occurs at multiple positions in one section
- **THEN** each returned slice retains the position tracked during construction rather than a position recovered by matching its text

### Requirement: Explicit token-counting policy
The system SHALL resolve a versioned token estimator from the active AI profile, map known OpenAI embedding models to `cl100k_base`, accept only supported explicit tokenizer identifiers, and use `utf8-byte-v1` conservatively when no exact mapping exists.

#### Scenario: Known embedding model
- **WHEN** the profile uses `text-embedding-ada-002`, `text-embedding-3-small`, or `text-embedding-3-large` without an explicit tokenizer
- **THEN** the effective tokenizer is `cl100k_base` and its count mode is exact

#### Scenario: Unknown model fallback
- **WHEN** neither a known model mapping nor a supported explicit tokenizer is available
- **THEN** the estimator counts one token per UTF-8 byte under `utf8-byte-v1` and reports a conservative count mode

#### Scenario: Unknown explicit tokenizer
- **WHEN** a profile declares an unsupported tokenizer identifier
- **THEN** the profile mutation is rejected instead of silently choosing another tokenizer

### Requirement: Deterministic chunker revision
The system SHALL compute an effective chunker revision from ordered behavior-affecting settings and versioned strategy, tokenizer, parser, and representation identities.

#### Scenario: Equivalent effective configuration
- **WHEN** two processing attempts resolve the same typed inputs regardless of map or JSON property order
- **THEN** they receive the same canonical settings hash and effective chunker revision

#### Scenario: Behavior-affecting input changes
- **WHEN** a strategy, tokenizer policy, or effective chunking value changes
- **THEN** subsequent processing receives a different effective chunker revision

### Requirement: Fixed-strategy baseline
The system SHALL retain the current fixed-character behavior behind the strategy contract and pin its output and cost baseline with deterministic fixtures before changing the production default.

#### Scenario: Compatibility mode
- **WHEN** the fixed strategy is selected with the existing effective settings
- **THEN** it produces the currently accepted fixed-window output while recording the new revision metadata
