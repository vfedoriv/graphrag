## Why

GraphRAG's user, operator, and contributor guidance has outgrown the single long `README.md`, making major workflows difficult to discover and allowing duplicated facts to drift. A canonical multipage portal is needed now to explain the implemented system end to end while keeping exhaustive REST schemas in the generated OpenAPI contract.

## What Changes

- Replace the monolithic documentation layout with a backend-owned, Markdown-first portal covering getting started, concepts, workflows, operations, references, and contributor guidance.
- Configure Maven Site to generate and locally preview navigable HTML without adding a Node or Python documentation toolchain.
- Add architecture, lifecycle, state, and data-flow diagrams using GitHub-compatible Mermaid fences and pinned browser-side rendering for the generated site.
- Reduce `README.md` to a concise landing page and migrate the existing onboarding and schema-draft guides into the canonical portal hierarchy.
- Add representative requests, responses, failure behavior, source maps, and links to runtime Swagger/OpenAPI instead of manually duplicating every DTO field.
- Add deterministic documentation navigation, link, asset, and implementation-alignment checks plus build-only CI verification.
- Establish stable reciprocal links to the separately maintained `graphrag-ui` documentation.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `documentation-alignment`: Expand repository documentation requirements from README-centric guidance to a canonical multipage portal with generated-site navigation, verified crosslinks, implementation-backed examples, and build validation.

## Impact

- Affected backend files: `README.md`, `docs/`, `src/site/`, `pom.xml`, documentation-alignment tests, contributor guidance, and a documentation CI workflow.
- Affected external system: the `graphrag-ui` repository will carry a coordinated change that links to the canonical portal while retaining its detailed frontend guides.
- New documentation commands: `./mvnw site` and `./mvnw site:run`.
- Runtime APIs, DTOs, persistence schemas, and application behavior are unchanged.
