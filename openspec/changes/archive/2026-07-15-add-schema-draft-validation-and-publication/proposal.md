## Why

A reviewed draft still needs corpus-level quality evidence and a controlled path into the existing schema registry. Validation, publication, activation, and document reprocessing must be explicit and independently observable so a weak draft or partial migration cannot silently replace the active extraction contract.

## What Changes

- Add held-out dry-run evaluation of a draft against selected knowledge-base documents, with durable evaluation runs and per-source results.
- Report deterministic extraction and validation metrics as contractual results while clearly labeling model-based query coverage and schema-noise judgments as advisory.
- Require publication preconditions covering resolved blocking conflicts, explicit decisions for guided candidates without evidence, successful structural validation, and an unoccupied target schema identity.
- Publish a draft projection through the existing schema registry as a normal inactive, editable schema associated with the knowledge base.
- Keep publication, schema activation, and reprocessing as three separate explicit operations.
- Add durable reprocessing plans that invoke existing overwrite processing semantics, expose aggregate progress and per-document failures, and support retry without hiding previously successful runs.
- Retain the draft, evidence, decisions, evaluations, and publication link as an audit trail after publication.

## Capabilities

### New Capabilities
- `schema-draft-evaluation`: Held-out source selection, dry extraction, deterministic quality metrics, advisory model assessments, and durable evaluation results.
- `schema-draft-publication`: Publication readiness, registry validation, creation of a normal inactive editable schema, idempotency, and audit linkage.
- `schema-reprocessing-plans`: Explicit post-activation document reprocessing plans, progress, per-document outcomes, and retry behavior.

### Modified Capabilities

None.

## Impact

- Depends on persistent drafts and review decisions from `add-persistent-schema-drafts`.
- Reuses existing schema parsing, validation, registration, association, activation, document processing, AI profile resolution, and observation behavior.
- Adds evaluation and reprocessing run persistence, controllers, application orchestration, and status APIs.
- Does not change the existing rule that inactive schemas may be edited or deleted and active schemas may not.
- Adds publication race/idempotency tests, evaluation fixtures, and end-to-end publish/activate/reprocess integration coverage.
