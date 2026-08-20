## 1. Establish the Maven documentation site

- [x] 1.1 Pin Maven Site Plugin 3.22.0 and Maven Fluido Skin 2.1.0 without binding site generation to the normal application build lifecycle
- [x] 1.2 Create `src/site/site.xml` with the six agreed navigation groups and stable backend/frontend repository links
- [x] 1.3 Add the site resources and pinned Mermaid 11.15.0 browser initializer for generated `language-mermaid` blocks
- [x] 1.4 Add the portal index and documentation-maintenance page, including `./mvnw site` and `./mvnw site:run` usage

## 2. Create getting-started and concept documentation

- [x] 2.1 Write the prerequisites, local-service provisioning, AI-profile startup, health check, and first end-to-end guide
- [x] 2.2 Write the system architecture and layer-boundary guide with a container/service flow diagram and implementation source map
- [x] 2.3 Write the PostgreSQL/Neo4j/filesystem persistence-ownership and operational-state guide with a data-boundary diagram
- [x] 2.4 Write the knowledge-base, schema, document, chunk, graph-fact, evidence, and provenance concept guide

## 3. Document major workflows

- [x] 3.1 Document knowledge-base lifecycle and AI-profile assignment, compatibility rejection, and secret-handling behavior
- [x] 3.2 Document schema creation, validation, generation, attachment, activation, guarded mutation, and schema format with representative payloads
- [x] 3.3 Migrate and expand the schema-draft guide across source collection, analysis, decisions, conflicts, evaluation, publication, activation, and reprocessing
- [x] 3.4 Document document upload, deduplication, parsing, chunking, embedding, graph extraction, provenance, replacement, deletion, overwrite, and failure cleanup
- [x] 3.5 Document chunking strategies, hierarchy, tokenizer/revision snapshots, inspection, migration preview, readiness, plan execution, retry, and known compatibility behavior
- [x] 3.6 Document Cypher generation, validation, `EXPLAIN`, limit/timeout policy, read-only execution, and one-shot `/ask` behavior
- [x] 3.7 Document advanced-search readiness, admission, durable lifecycle, retrieval branches, fusion/reranking, sufficiency, synthesis, citations, partial results, cancellation, and retention

## 4. Document operations and references

- [x] 4.1 Document startup properties, runtime-setting categories and lifecycle, canonical chunking aliases, and AI-profile-managed provider behavior
- [x] 4.2 Document AI observability, metadata-first logging, content-capture controls, Langfuse/Garage setup, and privacy constraints
- [x] 4.3 Document persistence provisioning and safety, deployment profiles, production-readiness concerns, and troubleshooting paths
- [x] 4.4 Create the curated API map, RFC 7807 error guide, schema-format reference, glossary, external references, and Swagger/OpenAPI handoff
- [x] 4.5 Migrate the existing onboarding guide into the contributor codebase tour and testing pages

## 5. Align repository entry points and guidance

- [x] 5.1 Replace the monolithic README with a concise project landing page that preserves required stack facts, quick-start commands, and portal/UI/OpenAPI links
- [x] 5.2 Remove or replace migrated `docs/ONBOARDING.md` and `docs/SCHEMA_DRAFTS.md` only after confirming whether pointer pages are needed for existing references
- [x] 5.3 Update `AGENTS.md` and `CLAUDE.md` with the canonical portal location, Maven documentation commands, and synchronized maintenance expectations
- [x] 5.4 Add canonical-backend notices and links to the coordinated frontend proposal, Advanced Search guide, and Chunking guide

## 6. Add deterministic documentation validation

- [x] 6.1 Extend `DocumentationAlignmentTest` to read stack, settings, and advanced-search facts from the canonical portal while preserving shared-guidance consistency checks
- [x] 6.2 Add deterministic checks for portal navigation coverage, relative Markdown links, image targets, and required reciprocal repository links without fetching external URLs
- [x] 6.3 Add a documentation-only CI workflow that runs the focused alignment test and Maven Site build and checks expected generated entry pages and Mermaid resources
- [x] 6.4 Run `./mvnw test -Pfast -Dtest=DocumentationAlignmentTest`, `./mvnw site`, and `./mvnw test -Pfast`
- [x] 6.5 Preview representative pages with `./mvnw site:run` and verify navigation, Mermaid diagrams, code examples, and reciprocal frontend links
