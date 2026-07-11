## Why

Normal `INFO` logs currently include truncated document, prompt, LLM-response, and extraction-payload content even when AI observability content capture is disabled. The test suite consequently produces hundreds of thousands of log tokens, and contributor guidance names framework versions that differ from the Maven build.

## What Changes

- Make operational logs metadata-first and prohibit user, document, prompt, model-response, query, and extracted-payload content from normal log levels.
- Keep privacy-controlled model content in the existing observability path with explicit capture settings and bounded summaries.
- Add test logging configuration that preserves actionable failures while suppressing routine framework and payload noise.
- Synchronize README, AGENTS, and CLAUDE version/configuration facts with `pom.xml` and add regression checks for shared guidance.

## Capabilities

### New Capabilities
- `privacy-safe-operational-logging`: defines content-safe structured logs and test-log noise controls independent of tracing capture.

### Modified Capabilities
- `ai-observability-monitoring`: separate privacy-controlled trace content from application logging and preserve observation metadata.
- `documentation-alignment`: make Maven dependency/version properties the source for documented stack facts.
- `test-coverage-governance`: require regression coverage for log privacy, logging configuration, and documentation alignment.

## Impact

Affected logging utilities and call sites, application/test logging configuration, observability tests, README, AGENTS, CLAUDE, and Maven test output. Public API responses are unchanged.
