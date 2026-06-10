## Why

The codebase has clear intended layering, but those boundaries are currently conventions rather than executable checks. As refactoring continues, accidental dependencies from controllers to persistence, domain to Spring infrastructure, or feature code to adapters could silently increase coupling.

## What Changes

- Add automated architecture boundary tests that encode the intended modular-monolith rules.
- Define allowed dependencies between API, application/service, domain, repository, adapter, and shared infrastructure packages.
- Start with rules that match the current codebase or explicitly identify transitional exceptions.
- Make future modularization work safer by detecting boundary regressions in tests.

## Capabilities

### New Capabilities
- `architecture-boundary-governance`: Defines executable dependency boundary checks for project modularity.

### Modified Capabilities

## Impact

- Affected tests: new architecture test suite, likely using ArchUnit or an equivalent Java dependency analysis library.
- Affected build: test dependency may be added if no suitable architecture-test library is already present.
- No runtime behavior or API contract changes are intended.
