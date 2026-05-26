## Why

The schema example generation endpoints currently return `example` values with escaped leading newlines and JSON encoded as noisy string content. This makes the payload harder to read and directly reuse in follow-up schema generation requests.

## What Changes

- Normalize output from `/api/v1/schemas/generate/example` and `/api/v1/schemas/generate/example/from-file` so `example` is clean JSON text without leading `\n` escape noise.
- Preserve current endpoint contracts and validation behavior, changing only response formatting/normalization for generated example content.
- Add tests that assert stable response formatting for both text-based and file-based example generation.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `schema-example-generation`: tighten response requirement so generated examples are returned as normalized, reviewable JSON text suitable for direct reuse.

## Impact

- Affected API responses: `/api/v1/schemas/generate/example`, `/api/v1/schemas/generate/example/from-file`.
- Affected service/response mapping in schema example generation flow.
- Tests for example generation response format will need updates/additions.
