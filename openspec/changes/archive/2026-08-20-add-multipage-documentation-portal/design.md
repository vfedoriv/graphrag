## Context

The backend currently concentrates most user, operator, API, and architectural guidance in an 886-line `README.md`, while `docs/ONBOARDING.md` and `docs/SCHEMA_DRAFTS.md` are not linked from that landing page. Detailed cross-stack Chunking and Advanced Search guides live in the frontend repository, and the backend documentation-alignment regression test assumes that exact contracts remain in README. The accumulated layout obscures major workflows and has already allowed contradictory statements to coexist.

The portal must remain readable as repository Markdown and as generated HTML. The project is Java/Maven-based, the documentation build must not introduce Node or Python, the first release is build-verified rather than deployed, and runtime Swagger/OpenAPI remains the exhaustive REST schema source.

## Goals / Non-Goals

**Goals:**

- Establish one backend-owned documentation hierarchy for users, operators, integrators, and contributors.
- Cover every major implemented workflow with diagrams, representative examples, failure behavior, related references, and implementation source maps.
- Generate and preview navigable HTML through the Maven Wrapper.
- Keep Markdown usable on GitHub and verify navigation, local links, assets, and selected implementation-backed facts.
- Provide stable reciprocal navigation to the frontend repository while allowing its existing deep dives to remain complete.

**Non-Goals:**

- Publishing or hosting the generated site in the first release.
- Replacing Swagger/OpenAPI with a hand-maintained exhaustive DTO reference.
- Changing APIs, persistence, runtime behavior, or frontend behavior.
- Automatically synchronizing duplicated prose across repositories.
- Incorporating unrelated repository-local documents such as `EVAL_FLOW.md` into the product portal.

## Decisions

### Use coordinated repo-local OpenSpec changes

The backend change owns the canonical portal and the frontend change owns frontend README, guide, and contributor-reference edits. Each proposal links the other and can be applied and archived within its own allowed edit root.

Alternative: track frontend edits as backend tasks. Rejected because OpenSpec resolved the backend action context as repo-local.

### Use Maven Site with Markdown sources

Pin Maven Site Plugin 3.22.0 and Maven Fluido Skin 2.1.0. Store canonical sources under `src/site/markdown`, navigation in `src/site/site.xml`, and browser resources under `src/site/resources`. `./mvnw site` generates `target/site`; `./mvnw site:run` provides local preview. Site generation remains a separate documentation lifecycle and is not bound to every normal package or test build.

Alternative: VitePress, Docusaurus, or MkDocs. Rejected because each adds a separate Node or Python documentation toolchain. AsciidoctorJ was rejected because it would replace GitHub-readable Markdown with AsciiDoc.

### Organize content by reader task and system workflow

The site navigation has six groups:

1. Getting Started: overview, prerequisites, local setup, and a first end-to-end run.
2. Concepts: architecture, persistence ownership, domain model, knowledge bases, schemas, chunks, graph facts, and provenance.
3. Workflows: knowledge-base/profile setup, schema registry lifecycle, schema drafts, document processing, chunking and reprocessing, Cypher query safety, and durable advanced search.
4. Operations: configuration, runtime settings, observability, deployment/persistence safety, and troubleshooting.
5. Reference: curated API map, schema format, error model, glossary, and external references.
6. Contributing: codebase tour, testing, and documentation maintenance.

The README retains only the project purpose, stack, prerequisites, short quick start, primary commands, and links to the portal, Swagger/OpenAPI, UI, and major workflows. Existing onboarding and schema-draft content moves into the matching site sections rather than remaining competing canonical pages.

### Keep exact API schemas in OpenAPI

Portal pages document workflow ordering, ownership, invariants, representative curl/JSON payloads, expected status classes, and recovery behavior. They link to runtime Swagger UI and `/v3/api-docs` for exhaustive endpoint and DTO schemas. This reduces manual duplication while preserving practical examples.

### Render portable Mermaid diagrams without a build toolchain

Authors use standard fenced `mermaid` blocks, which GitHub renders directly. Maven Site injects a small local initializer through the site descriptor; that initializer imports a fixed Mermaid 11.15.0 browser module and transforms generated `language-mermaid` blocks. No Node/Vite installation or generated diagram files are required.

The diagrams cover system boundaries, persistence ownership, schema and draft states, ingestion/chunking/extraction, reprocessing, query validation, advanced-search execution, and durable run states.

### Validate documentation with repository tests and CI

Extend the existing Java documentation-alignment regression tests rather than adopting the retired Maven Linkcheck Plugin. The tests resolve same-repository Markdown links and image targets, ensure every portal page is represented in navigation, check required reciprocal repository links, and read stack/contract assertions from canonical pages. External URLs are not fetched during deterministic tests.

A documentation workflow runs the focused alignment test and `./mvnw site` when portal, README, contributor guidance, Maven site configuration, or documentation tests change. It verifies the expected generated entry pages and Mermaid initializer are present but does not deploy them.

### Keep frontend deep dives complete but mark ownership

The frontend Advanced Search and Chunking guides retain their full cross-stack explanations and screenshots. They gain a canonical-backend notice and reciprocal links; backend pages link back to the UI guides for controls, screenshots, caveats, and frontend source maps. Stable cross-repository links target the `main` branch.

## Risks / Trade-offs

- [Duplicated frontend deep dives can drift] → Mark the backend portal canonical, retain verification dates, and add reciprocal links plus maintenance guidance in both repositories.
- [Browser Mermaid rendering needs network access] → Pin the module version, keep source fences GitHub-renderable, and document that Maven Site HTML needs access to the pinned module; vendoring can be added later if offline preview becomes required.
- [Maven Site has a traditional UI and no local full-text search] → Prefer predictable navigation, sitemap, headings, and a glossary; search and publishing remain future enhancements.
- [Moving existing pages can break undocumented external links] → Search repository references before moving and leave short pointer pages only where an existing inbound path must be preserved.
- [Manually maintained examples can drift] → Keep examples representative, assert high-risk contract tokens in tests, and point exhaustive shapes to OpenAPI.

## Migration Plan

1. Add the Maven Site configuration, navigation skeleton, Mermaid initializer, and documentation checks.
2. Move existing README, onboarding, and schema-draft material into the portal hierarchy, correcting facts against code, configuration, and active specs.
3. Add missing workflow, operations, reference, and contributor pages.
4. Replace README with the concise landing page and update backend contributor guidance.
5. Apply the coordinated frontend change and verify reciprocal links.
6. Run focused documentation tests, build the site, preview representative pages, and then run the fast backend suite.

Rollback consists of reverting the documentation and Maven-site commits; there is no runtime or data migration.

## Open Questions

None. Site publishing, offline Mermaid vendoring, and full-text search are explicitly deferred.
