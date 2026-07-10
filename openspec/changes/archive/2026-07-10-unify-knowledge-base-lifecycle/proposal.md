## Why

Knowledge bases are currently created through more than one path: explicit creation assigns the default AI profile, while schema activation can create a partial knowledge base. Deleting a knowledge base also deletes only its node, leaving document records and files behind; filesystem writes are not coordinated with Neo4j transaction outcomes.

## What Changes

- Route explicit and schema-activation knowledge-base creation through one lifecycle service that always seeds required defaults.
- **BREAKING** Reject knowledge-base deletion while owned documents remain, rather than leaving inaccessible document data and binaries.
- Repair legacy document-to-knowledge-base ownership before enforcing lifecycle checks.
- Make document binary mutations recoverable with durable operation tracking and reconciliation after database or storage failures.

## Capabilities

### New Capabilities
- `knowledge-base-lifecycle`: defines consistent provisioning, ownership validation, and safe deletion behavior for knowledge bases.
- `document-storage-reconciliation`: tracks and reconciles non-transactional binary-storage operations associated with document lifecycle changes.

### Modified Capabilities
- `ai-profile-management`: require default-profile assignment for every supported knowledge-base provisioning path.
- `document-management`: require valid knowledge-base ownership and recoverable storage behavior for upload, replacement, and deletion.
- `single-active-schema-per-knowledge-base`: make implicit schema-activation provisioning use the common knowledge-base lifecycle.

## Impact

Affected knowledge-base, schema, document upload/replacement/deletion services; Neo4j ownership migration; local binary storage; API error contracts; and lifecycle integration tests.
