## Why

Clients currently cannot retrieve the schemas associated with a specific knowledge base through a dedicated API operation. Adding a targeted listing endpoint improves discoverability and removes the need for indirect or client-side filtering logic.

## What Changes

- Add a new `GET` endpoint in `SchemaController` to return schemas associated with a specified knowledge base.
- Define endpoint contract details for path/query parameters, response payload shape, and error behavior.
- Ensure retrieval includes only schemas linked to the requested knowledge base and keeps existing schema immutability constraints intact.

## Capabilities

### New Capabilities
- `schema-list-by-knowledge-base`: Provide an API capability to list schemas associated with a specific knowledge base.

### Modified Capabilities

## Impact

- Affected API surface under `/api/v1` in `SchemaController`.
- Likely affected service/repository query paths for schema lookup by knowledge base association.
- Integration and controller tests will need additions for successful listing and error cases (for example, missing or unknown knowledge base).
