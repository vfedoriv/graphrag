# External references

## Project repositories

- [GraphRAG backend repository](https://github.com/vfedoriv/graphrag) — canonical runtime behavior and this portal. Current detailed work is maintained on the `main` branch.
- [GraphRAG UI repository](https://github.com/vfedoriv/graphrag-ui) — frontend controls, screenshots, and browser behavior; current detailed work is maintained on `main`.
- [Advanced Search UI guide](https://github.com/vfedoriv/graphrag-ui/blob/main/docs/ADVANCED_SEARCH.md)
- [Chunking UI guide](https://github.com/vfedoriv/graphrag-ui/blob/main/docs/CHUNKING.md)
- [Coordinated frontend documentation proposal](https://github.com/vfedoriv/graphrag-ui/tree/main/openspec/changes/add-multipage-documentation-portal)

## Runtime and libraries

- [Spring Boot reference](https://docs.spring.io/spring-boot/reference/)
- [Spring AI reference](https://docs.spring.io/spring-ai/reference/)
- [Spring Data Neo4j reference](https://docs.spring.io/spring-data/neo4j/reference/)
- [Neo4j Cypher manual](https://neo4j.com/docs/cypher-manual/current/)
- [PostgreSQL 17 documentation](https://www.postgresql.org/docs/17/)
- [LangChain4j documentation](https://docs.langchain4j.dev/)
- [OpenTelemetry documentation](https://opentelemetry.io/docs/)
- [Langfuse documentation](https://langfuse.com/docs)
- [Testcontainers for Java](https://java.testcontainers.org/)

## Documentation toolchain

- [Maven Site Plugin](https://maven.apache.org/plugins/maven-site-plugin/)
- [Maven Fluido Skin](https://maven.apache.org/skins/maven-fluido-skin/)
- [Mermaid documentation](https://mermaid.js.org/)

External URLs are intentionally not fetched by deterministic repository tests. Update version-specific behavior only after checking the current official documentation; keep exact backend request schemas in runtime Swagger/OpenAPI.
