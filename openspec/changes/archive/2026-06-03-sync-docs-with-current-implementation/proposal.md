## Why

The repository documentation has drifted from the current implementation, which creates avoidable confusion for contributors and API consumers. This should be corrected now because the code already exposes endpoints, limits, and request contracts that are either missing or described inaccurately in the repo guidance.

## What Changes

- Update `README.md` to match the live API surface, configuration values, and request contracts.
- Update `AGENTS.md` so coding-agent guidance reflects the current controller surface and documentation maintenance expectations.
- Update `CLAUDE.md` so contributor guidance stays aligned with the current implementation and documented API surface.
- Add a documentation alignment capability in OpenSpec so future documentation drift can be tracked explicitly.

## Capabilities

### New Capabilities
- `documentation-alignment`: Repository guidance documents must stay consistent with the implemented API surface, configuration defaults, and contributor workflows.

### Modified Capabilities

## Impact

- Affected files: `README.md`, `AGENTS.md`, `CLAUDE.md`
- Affected process: contributor onboarding, API usage guidance, coding-agent instructions
- Runtime impact: none
