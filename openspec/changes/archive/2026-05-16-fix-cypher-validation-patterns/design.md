## Context

`CypherValidationService` validates generated and user-submitted read queries against the active schema before running `EXPLAIN`. It currently extracts schema references with regular expressions:

- `LABEL_PATTERN` matches any `:Name` token.
- `REL_PATTERN` only captures the first relationship type after `[:`.
- `PROPERTY_PATTERN` captures simple `alias.property` references.

Because `LABEL_PATTERN` is context-free, it can match a relationship type alternative after `|`, for example `[:HAS_GREASE_RECOMMENDATION|REQUIRES_GREASE]`, and report it as an unknown label. This makes valid schema-constrained queries fail before Neo4j can plan them.

## Goals / Non-Goals

**Goals:**
- Correctly distinguish node labels from relationship types in common Cypher read patterns.
- Validate every relationship type in relationship unions.
- Keep validation conservative and deterministic before `EXPLAIN`.
- Add tests that document the supported pattern surface and the previous false rejection.

**Non-Goals:**
- Building a full Cypher parser.
- Expanding query support beyond the existing read-only validation contract.
- Changing public API responses, query execution semantics, or schema storage.

## Decisions

1. Replace broad schema-reference regex use with context-aware extraction helpers.

   The service should scan node patterns and relationship patterns separately so `:Label` in `(n:Label)` is not treated the same as `:TYPE` in `[r:TYPE]`. Regex can still be used inside those bounded contexts, but the boundary selection must distinguish `(...)` node patterns from `[...]` relationship patterns.

   Alternative considered: tighten `LABEL_PATTERN` with negative lookbehind around `[` and `|`. That is brittle because labels can appear in multiple node syntaxes, relationship types can include aliases and unions, and future corner cases would continue to depend on incidental punctuation.

2. Extract all relationship union members.

   Relationship validation must split the type segment after `:` on `|`, trim optional backticks, and validate each type against the allowed relationship types. This covers both `[:TYPE_A|TYPE_B]` and `[r:TYPE_A|TYPE_B]`.

   Alternative considered: one expanded relationship regex with repeated captures. Java regex APIs make repeated capture groups awkward and easy to under-test; a small helper is clearer.

3. Keep property extraction scoped to simple qualified property reads.

   The current property allow-list behavior should remain focused on `alias.property` references. This proposal does not require parsing map literals or dynamic property access because the existing validator already relies on Neo4j `EXPLAIN` for syntax and planner validation after schema reference checks.

## Risks / Trade-offs

- [Risk] Context-aware scanning can miss unsupported Cypher forms. -> Mitigation: preserve `EXPLAIN` validation and add tests for the query forms the application generates and accepts.
- [Risk] A lightweight extractor may still be less complete than a full Cypher parser. -> Mitigation: keep the implementation small, explicit, and covered by corner-case tests rather than broadening support implicitly.
- [Risk] Backtick-quoted schema names with uncommon characters may need broader handling later. -> Mitigation: cover current schema identifier conventions and include quoted simple identifiers in tests.
