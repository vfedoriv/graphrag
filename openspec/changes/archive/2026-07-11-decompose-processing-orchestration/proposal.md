## Why

Document processing, runtime settings, and schema generation combine orchestration, persistence, provider resolution, serialization, validation, and formatting in several 400–750 line services. That increases change risk, duplicates embedding/client policy, and makes focused tests depend on broad service construction.

## What Changes

- Decompose document processing into explicit stage-oriented application components while preserving its public service contract and run-history semantics.
- Split runtime settings catalog, value parsing, override persistence, and live application into typed cohesive components.
- Split schema generation prompt construction, model invocation, graph-document mapping, and warnings into independently testable components.
- Strengthen architecture rules so workflow dependencies and direct Neo4j access remain explicit as the new components are introduced.

## Capabilities

### New Capabilities
- `application-workflow-orchestration`: defines explicit, observable, testable application workflow stages and typed settings composition boundaries.

### Modified Capabilities
- `document-processing-run-history`: preserve processing-run state transitions through stage-oriented orchestration.
- `service-helper-utilities`: extend cohesive deterministic support extraction to workflow and settings components.
- `architecture-boundary-governance`: govern new workflow, adapter, and database-client boundaries.

## Impact

Affected document processing, runtime settings, schema generation, AI client resolution, application package layout, unit tests, architecture tests, and developer-facing maintenance guidance. No public HTTP contract is intended to change.
