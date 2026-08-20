# Testing

Use the Maven Wrapper. AI clients are mocked/faked for deterministic tests, so neither the fast nor complete suite needs external provider credentials.

## Fast deterministic suite

```bash
./mvnw test -Pfast
```

The fast profile excludes container-backed integration coverage and is the normal inner loop. For portal or shared-guidance changes:

```bash
./mvnw test -Pfast -Dtest=DocumentationAlignmentTest
./mvnw site
```

## Complete suite

```bash
./mvnw test
```

Application integration tests share one JVM-scoped PostgreSQL and Neo4j container, reset relational/graph/filesystem/runtime-setting/fake state before each test, and run sequentially. Fresh-server provisioning/startup tests use independent PostgreSQL containers. Docker is required, but model credentials are not.

Focused full-flow verification:

```bash
./mvnw test -Dtest=EndToEndMvpFlowIntegrationTest
```

Place Maven arguments after the goal so repository execution rules match.

## Performance inventory

```bash
./scripts/measure-test-suite.sh
```

Successful timing, test inventory, Spring context count, container lifecycle count, and slowest-test reports are stored under `target/test-performance/<run-name>/`.

## Coverage map

- `EndToEndMvpFlowIntegrationTest`: canonical schema → KB → upload/process → query flow.
- Document processing integration tests: chunks, embeddings, indexes, graph extraction, overwrite, and cleanup.
- Schema registry/draft/publication/reprocessing tests: identity, revisions, durable item outcomes, and recovery.
- Cypher validation/execution tests: blocked operations, schema validation, `EXPLAIN`, limit, timeout, normalization.
- Advanced-search tests: readiness/admission, durable lifecycle, branches, fusion/reranking, citations, partial/cancel/retention behavior.
- Runtime settings/profile tests: catalog lifecycle, live application, secret masking, and embedding compatibility.
- `DocumentationAlignmentTest`: portal navigation/local targets/reciprocal URLs and implementation-backed shared facts.

## Choosing verification

Run the focused unit/controller test during development, then `-Pfast`. Run the complete suite for persistence, cleanup, migration, lifecycle, startup, or end-to-end behavior. Documentation-only changes must run the focused alignment test and Maven Site build; preview representative pages before final review.

Testcontainers commands require Docker access in restricted coding environments. Do not weaken or skip container coverage merely to make a sandboxed run pass; request the required execution permission.
