## Context

The project has intended layers and feature areas, but most boundaries are conventions. Existing packages mix technical layers (`controller`, `service`, `repository`, `domain`) with capability packages (`schema`, `graph`, `query`, `document`, `embedding`, `storage`). Architecture tests can make the intended dependency direction explicit while allowing transitional exceptions for current persistence-shaped domain nodes and DTO mapping.

## Goals / Non-Goals

**Goals:**
- Add executable architecture tests that protect against new coupling.
- Start with rules that are useful immediately and do not require a large repackage first.
- Document transitional exceptions where current code intentionally violates the future ideal.
- Provide a path to tighten rules after application/domain boundaries are refactored.

**Non-Goals:**
- Repackaging the codebase in this change.
- Removing all controller-to-domain DTO mapping immediately.
- Removing Spring Data Neo4j annotations from persistence node classes.
- Enforcing a full hexagonal architecture in one step.

## Decisions

- Use ArchUnit unless a simpler existing dependency-analysis tool is already present.
  - Rationale: ArchUnit is standard for Java package dependency rules and integrates with JUnit.
- Begin with high-signal rules:
  - controllers must not depend on repositories or `Neo4jClient`;
  - repositories must only expose persistence/domain node types and Spring Data infrastructure;
  - domain node classes must not depend on controllers, services, DTOs, or repositories;
  - feature services should not depend on controllers;
  - direct `Neo4jClient` usage must remain in approved graph persistence/application components.
- Capture transitional exceptions in one place.
  - Rationale: tests should guide refactoring without forcing a broad rewrite.
- Run architecture tests as part of normal Maven tests.

## Risks / Trade-offs

- Boundary tests can become brittle -> Start with few high-value rules and clear exception names.
- Current code has transitional layering compromises -> Document exceptions and treat them as debt to burn down.
- Dependency addition increases build surface -> Keep the dependency test-scoped.
