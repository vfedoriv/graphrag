# Migrate Schemas From YAML To JSON

## Summary

Migrate the project to JSON-only schema definitions. New schema content, generated schemas, bootstrap resources, API docs, tests, and persisted `SchemaFormat` values will use JSON. Runtime YAML parsing will be removed.

Existing persisted YAML schemas will be considered incompatible after this migration. Because schema versions are immutable, the app should fail fast or document that existing Neo4j schema data must be cleared/recreated or manually migrated before upgrade.

## Key Changes

- Replace `SchemaParser` YAML `ObjectMapper(new YAMLFactory())` with standard Jackson JSON parsing.
- Rename YAML-specific service methods and variables:
  - `validateYaml` -> `validateJson`
  - `generateYaml` -> `generateJson`
  - logs/errors/docs from "YAML" to "JSON"
- Store all newly created schemas with `SchemaFormat.JSON`.
- Update `SchemaBootstrapService` to load `classpath:/schemas/*.json`.
- Convert bootstrap files:
  - `legal-contracts-v1.yaml` -> `legal-contracts-v1.json`
  - `cmms-v1.yaml` -> `cmms-v1.json`
- Convert test fixtures from `.yaml` to `.json`.
- Remove `jackson-dataformat-yaml` from `pom.xml` if no other code needs it.
- Update OpenAPI annotations, DTO examples, README, AGENTS/CLAUDE references, and OpenSpec requirements from YAML to JSON.

## Runtime Compatibility Policy

- Do not support YAML input after migration.
- `POST /api/v1/schemas`, `POST /api/v1/schemas/validate`, schema generation responses, and stored schema content all expect JSON text.
- Existing `SchemaDefinition` nodes with `format=YAML` should not be silently used.
- Add a clear startup/runtime failure path when an active or bootstrapped schema is not JSON, with guidance to recreate or migrate data.
- Keep the `SchemaFormat.YAML` enum value only if needed to read old records and produce explicit incompatibility errors; otherwise remove it if no serialized data compatibility is required.

## Test Plan

- Update `SchemaParserValidatorTest` to parse valid JSON and reject invalid JSON.
- Update `SchemaRegistryIntegrationTest` to create, persist, activate, and deduplicate JSON schemas with `SchemaFormat.JSON`.
- Update E2E tests to load `fixtures/schemas/contracts-v1.json`.
- Update controller tests to expect JSON generation content and `SchemaFormat.JSON`.
- Update schema generation tests so generated content parses as JSON.
- Run `./mvnw test`.

## Assumptions

- The target is a breaking JSON-only migration, not dual-format support.
- Existing Neo4j data can be recreated or migrated outside normal runtime before upgrading.
- The schema object model does not change; only the wire/storage authoring format changes.
