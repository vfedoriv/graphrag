## 1. Characterization and Boundaries

- [x] 1.1 Add characterization tests for processing stage order, run lifecycle, status transitions, and failure behavior.
- [x] 1.2 Define application workflow and infrastructure adapter packages and update architecture tests with narrow transitional exceptions.
- [x] 1.3 Introduce injected shared serialization and one reusable profile-scoped AI client resolver.

## 2. Document Processing Extraction

- [x] 2.1 Extract processing-run lifecycle, option resolution, source parsing, chunk preparation, and chunk metadata collaborators.
- [x] 2.2 Extract embedding persistence and graph extraction stages behind the existing `DocumentProcessingService` facade.
- [x] 2.3 Preserve observations, error handling, and persisted run semantics through focused unit and integration tests.

## 3. Settings and Schema Generation Extraction

- [x] 3.1 Split runtime settings catalog definitions, codecs, override persistence, lifecycle calculation, and live appliers.
- [x] 3.2 Ensure clearing a live setting reapplies its startup default and add regression coverage for logging level.
- [x] 3.3 Split schema generation into prompt, model-call, graph-to-schema mapping, and warning collaborators with unit coverage.

## 4. Governance and Verification

- [x] 4.1 Move direct database calls for the affected workflows to named adapters and freeze unrelated legacy exceptions behind governed package boundaries.
- [x] 4.2 Remove each affected-workflow transitional architecture exception after its dependent workflow migration completes.
- [x] 4.3 Run architecture, unit, focused integration, and full Maven test suites without weakening existing assertions.
