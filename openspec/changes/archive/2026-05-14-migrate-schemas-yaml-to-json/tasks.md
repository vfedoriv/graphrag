## 1. Parser and Format Contract

- [x] 1.1 Replace YAML parsing/validation entry points with JSON-only parsing and validation methods.
- [x] 1.2 Update schema format assignment so new schemas persist as `SchemaFormat.JSON`.
- [x] 1.3 Remove `SchemaFormat.YAML` and related YAML compatibility branches from runtime code.

## 2. Schema Generation and Bootstrap

- [x] 2.1 Update schema generation services/endpoints to produce and describe generated schema content as JSON.
- [x] 2.2 Switch bootstrap loading pattern to `classpath:/schemas/*.json` and validate loaded JSON schema definitions.
- [x] 2.3 Convert bundled bootstrap schema files from YAML to JSON and remove old YAML resources.

## 3. API, Documentation, and Tests

- [x] 3.1 Update API annotations, DTO examples, and error text from YAML terminology to JSON terminology.
- [x] 3.2 Convert schema-related test fixtures from `.yaml` to `.json` and update test expectations to `SchemaFormat.JSON`.
- [x] 3.3 Remove `jackson-dataformat-yaml` from `pom.xml` if no remaining code paths require it.
- [x] 3.4 Run `./mvnw test` and fix any regressions caused by the JSON-only migration.
- [x] 3.5 Document and execute pre-upgrade cleanup of persisted YAML schema records in Neo4j.
