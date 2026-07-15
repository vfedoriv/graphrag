## Why

Durable schema-draft runs and reprocessing plans can currently be read only when the client already knows their identifiers. Browser reloads, concurrent sessions, and lost local state therefore make active or historical work undiscoverable even though the backend still owns the authoritative workflow state.

## What Changes

- Add paginated analysis-run and evaluation-run history endpoints under each schema draft.
- Add a knowledge-base reprocessing-plan history endpoint filterable by draft identifier.
- Return typed summaries with identifiers, statuses, timestamps, retryability, retry lineage, and explicit current/latest semantics without embedding full per-source or per-document outcomes.
- Record analysis retry lineage and calculate whether analysis and evaluation runs still match the current draft state.
- Add an evaluation-eligible-documents endpoint that applies the same authoritative active-evidence rule used when evaluation starts and returns an explicit eligibility reason per owned document.
- Add lightweight current-analysis, latest-evaluation, and latest-reprocessing references to draft list and detail responses so overview screens and polling recovery do not require full history scans.
- Keep individual status endpoints authoritative for detailed progress and paged outcomes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `schema-draft-lifecycle`: Draft responses expose lightweight current/latest workflow references.
- `schema-draft-analysis`: Clients can discover run history, current status, and retry lineage after losing a run identifier.
- `schema-draft-evaluation`: Clients can discover evaluation history/currentness and query authoritative held-out document eligibility.
- `schema-reprocessing-plans`: Clients can discover draft-scoped plan history and the latest plan without retaining a plan identifier.

## Impact

- Affects draft, analysis, evaluation, and reprocessing controllers, DTOs, services, Neo4j repository queries, and API/integration tests.
- Adds analysis retry-lineage persistence for newly retried runs; historical runs without lineage remain valid roots.
- Requires bounded, deterministically ordered page queries and ownership-safe filtering for all new list endpoints.
- Builds on the standard page envelope from `harden-schema-draft-api-contracts`; implement that change first or coordinate the shared response type during implementation.
