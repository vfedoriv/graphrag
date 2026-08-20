# Documentation maintenance

The Markdown portal in `src/site/markdown` is the canonical detailed documentation. `README.md` is a landing page, Swagger/OpenAPI owns exhaustive REST schemas, and the frontend repository owns UI controls and screenshots.

## Authoring rules

1. Put durable workflow and operational guidance in the matching portal page.
2. Keep representative payloads small and link to Swagger UI and `/v3/api-docs` for every field.
3. Use relative `.md` links between portal sources. Use stable `https://github.com/vfedoriv/.../blob/main/...` links across repositories.
4. Write diagrams as fenced `mermaid` blocks. Do not check in generated diagrams or add Node/Python tooling.
5. Update `src/site/site.xml` whenever a page is added, moved, or removed.
6. Keep overlapping stack and contributor facts synchronized in `README.md`, `AGENTS.md`, and `CLAUDE.md`.
7. Verify values against `pom.xml`, `application*.properties`, controllers, services, and active OpenSpec decisions before documenting them.
8. Normal logs must remain metadata-first; content-capture guidance belongs in [observability and privacy](../operations/observability.md).

## Local validation

```bash
./mvnw test -Pfast -Dtest=DocumentationAlignmentTest
./mvnw site
./mvnw site:run
```

The focused test checks navigation coverage, same-repository Markdown links and image targets, reciprocal repository URLs, shared contributor guidance, and selected implementation-backed facts. It deliberately does not fetch external URLs. The site build must produce the expected entry pages and copy `js/mermaid-init.js`.

For a final review, open the portal overview, architecture, one workflow with JSON, and advanced search. Verify the six navigation groups, diagram rendering, code formatting, and frontend links. Then run `./mvnw test -Pfast`.

## Ownership and coordinated changes

- Backend source and canonical documentation: [vfedoriv/graphrag](https://github.com/vfedoriv/graphrag)
- Frontend source and UI documentation: [vfedoriv/graphrag-ui](https://github.com/vfedoriv/graphrag-ui)
- Coordinated frontend proposal: [add reciprocal canonical documentation links](https://github.com/vfedoriv/graphrag-ui/tree/main/openspec/changes/add-multipage-documentation-portal)

Backend behavior changes should update this portal in the same change. Frontend-only interaction changes stay in the UI repository; update reciprocal links when ownership or paths change.
