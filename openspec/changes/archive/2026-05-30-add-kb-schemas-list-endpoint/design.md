## Context

The current schema API supports creating and retrieving schemas, but it lacks a direct operation to list schemas tied to one knowledge base. Consumers that need this view currently depend on broader listing or multiple calls, which increases coupling and client complexity. The system must preserve current constraints: immutable schema versions, RFC 7807 errors, and controller-service-repository layering.

## Goals / Non-Goals

**Goals:**
- Add a read-only endpoint under `/api/v1` that returns schemas associated with a specified knowledge base.
- Reuse existing schema DTO conventions so clients receive consistent fields.
- Keep behavior deterministic for both empty-result and not-found knowledge base cases.
- Ensure test coverage at controller/service layers for success and error scenarios.

**Non-Goals:**
- Changing schema versioning or activation semantics.
- Introducing write/update behavior in this endpoint.
- Redesigning global schema listing behavior outside knowledge-base-scoped retrieval.

## Decisions

- Add a dedicated controller route in `SchemaController` using a knowledge-base identifier as input (path or query, finalized during implementation based on existing controller style).
  - Rationale: Keeps contract explicit and avoids overloading unrelated endpoints.
  - Alternative considered: Reusing existing schema list endpoint with optional filter parameter; rejected because it weakens API clarity and makes validation/error semantics less explicit.
- Implement retrieval through service and repository methods that filter by knowledge base association.
  - Rationale: Preserves layering and keeps query logic in the repository.
  - Alternative considered: Controller-level composition of existing methods; rejected due to duplicated orchestration and weaker testability.
- Return `200` with list payload when knowledge base exists, including an empty list when it has no schemas.
  - Rationale: Distinguishes "known KB with no schemas" from lookup failures while keeping REST list semantics.
  - Alternative considered: Returning `404` for empty associations; rejected because absence of child resources is not missing parent resource.
- Return existing not-found `ProblemDetail` when the knowledge base identifier does not resolve.
  - Rationale: Aligns with established API error model and avoids ambiguous empty responses for invalid identifiers.

## Risks / Trade-offs

- [Risk] Ambiguity in endpoint shape (path vs query) could cause inconsistency with existing controllers. -> Mitigation: Follow current controller patterns in codebase and document exact route in spec.
- [Risk] Additional lookup for knowledge base existence may add query overhead. -> Mitigation: Use efficient repository checks and keep endpoint read-only.
- [Risk] DTO expansion or inconsistent fields across list endpoints. -> Mitigation: Reuse existing schema response DTO mappings.
