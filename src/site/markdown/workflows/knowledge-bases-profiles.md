# Knowledge bases and AI profiles

A knowledge base is the ownership boundary for schemas, documents, retrieval, and queries. Its ID is supplied by the client. Each knowledge base has one active AI profile, and new knowledge bases inherit the persisted default profile.


Knowledge-base API, lifecycle state, management, and relational adapters live under
`knowledgebase`; profile API/state/persistence live under `ai.profiles`. Foreign
workflows use immutable `AiProfileAccess` facts and knowledge-base public
capabilities. Provider keys and clients remain AI-owned. Captured execution and
revision-scoped caches preserve model selection and nested scope restoration;
profile/assignment reads retain caller transaction participation.

## Knowledge-base lifecycle

```bash
curl -X POST http://localhost:8080/api/v1/knowledge-bases \
  -H "Content-Type: application/json" \
  -d '{"id":"kb-contracts","name":"Contracts"}'

curl http://localhost:8080/api/v1/knowledge-bases/kb-contracts

curl -X PUT http://localhost:8080/api/v1/knowledge-bases/kb-contracts \
  -H "Content-Type: application/json" \
  -d '{"name":"Production contracts"}'
```

Use the same ID for every KB-scoped route. Deleting a knowledge base is guarded by its owned resources and invokes cross-store cleanup; treat it as destructive. Schema activation can lazily create a missing knowledge base for backward compatibility, but explicit creation makes profile assignment and ownership visible.

## Create and manage profiles

Profiles are OpenAI-compatible configurations persisted in PostgreSQL and revisioned on change:

```bash
curl -X POST http://localhost:8080/api/v1/ai-profiles \
  -H "Content-Type: application/json" \
  -d '{
    "id":"openai-primary",
    "name":"OpenAI primary",
    "baseUrl":"https://api.openai.com/v1",
    "apiKey":"<secret>",
    "chatModel":"gpt-5-mini",
    "embeddingModel":"text-embedding-3-small",
    "tokenizerId":"cl100k_base",
    "embeddingDimensions":1536,
    "timeoutSeconds":60,
    "maxRetries":2,
    "structuredOutputMode":"PORTABLE",
    "defaultProfile":true
  }'
```

`POST/GET /ai-profiles`, `GET/PUT/DELETE /ai-profiles/{profileId}` provide CRUD. Setting a new default affects knowledge bases created later; it does not silently reassign existing knowledge bases.

## Structured output mode

`structuredOutputMode` is a non-secret, revisioned profile field exposed by profile
reads and KB profile configuration reads. Supported values are `PORTABLE` and
`NATIVE_JSON_SCHEMA`. Creation with an omitted/null value, startup seeding, and
migration of existing profiles all use `PORTABLE`. Startup preserves an existing
saved mode. A PUT omitting the mode or supplying null retains the saved mode;
older clients can update a native profile without resetting it. Unsupported values
return the existing HTTP 400 problem response without changing the profile.

Set `NATIVE_JSON_SCHEMA` explicitly only after checking the configured endpoint and
model's strict JSON Schema support. Saving does not probe the provider, and an
OpenAI-compatible URL/client class does not establish support. In particular, keep
LM Studio portable unless that model/endpoint has been verified. Opt-in applies
only to document graph extraction and Cypher generation, including `/ask` and
other workflows reusing that Cypher client. Schema discovery/draft candidate
analysis, schema generation, planning, reranking, sufficiency, and synthesis keep
their portable requests.

Eligible native calls attach a strict, closed, fixed workflow schema per request.
Private entry arrays represent dynamic properties, endpoint keys, and parameters;
accepted output becomes the existing ordinary maps/lists/scalars. Public responses
do not expose native envelopes. Graph normalization/filtering/limits and Cypher
read-only/schema/limit/`EXPLAIN` checks still apply.

Refusal, incomplete completion, empty normal content (including reasoning-only
JSON), invalid native output, and native format rejection/unavailability fail the
call explicitly. There is no portable fallback or new output-repair retry; the
configured SDK retry policy remains in effect. Provider authentication, throttling,
transport, and timeout failures retain ordinary provider handling. Observations
record `ai.output.mode`, `ai.output.contract`, and `ai.output.outcome` safely;
ordinary logs do not include payloads, refusal/reasoning text, or provider messages.
Existing controlled trace capture settings still apply.

To roll back, PUT the full ordinary profile configuration with
`"structuredOutputMode":"PORTABLE"` (omit `apiKey` to retain the saved key). Mode
changes advance the revision and invalidate subsequent model resolutions without
rebuilding embeddings. Combined incompatible embedding edits still fail atomically.
Already captured operations keep their captured model and mode; later captures use
the updated revision. No existing run snapshots or artifacts are rewritten.

### Optional provider comparison

Credential-free tests verify real SDK requests/responses against local HTTP stubs,
recursive value conversion, and workflow guardrails. Live provider measurements are
optional and require configured credentials; they are not a release criterion.

For a manual comparison, use the same endpoint/model, active schema, document
corpus and query questions, timeout, and SDK retry settings. Record those inputs
and both rendered workflow prompts: portable and native prompts necessarily differ
in their output-format instructions; keep the semantic/source content identical.
Run the corpus with `PORTABLE`, then explicitly select `NATIVE_JSON_SCHEMA` and
repeat with fresh attempts. Use a test KB or an explicit overwrite workflow to
avoid reusing completed results. Capture per-attempt parse failures, safe failure
categories, semantic accepted/dropped node and relationship counts, query validation
outcomes, latency, and input/output token usage from observations. Keep refusals and
incomplete output separate from parse errors. Report sample size and distributions,
including entry-encoding token overhead; do not infer correctness or improved
quality/cost/latency from format conformance. Restore `PORTABLE` explicitly after
the comparison if native support is unsuitable.

## Assign a profile

```bash
curl -X PUT \
  http://localhost:8080/api/v1/knowledge-bases/kb-contracts/ai-profile \
  -H "Content-Type: application/json" \
  -d '{"profileId":"openai-primary"}'
```

The assigned profile is resolved at runtime for processing, embedding, graph extraction, Cypher generation, `/ask`, advanced search, knowledge-base-scoped schema generation, and schema discovery/draft work. Running durable work retains its captured profile ID/revision even if assignment changes later.

## Embedding compatibility

Once chunks exist, assignment and profile mutation must preserve the embedding space: normalized provider/base URL, embedding model, dimensions, and resolved tokenizer identity. Known OpenAI embedding models resolve automatically to `cl100k_base`; an unknown model uses the versioned conservative `utf8-byte-v1` estimator unless the profile explicitly selects the supported `cl100k_base` tokenizer.

An incompatible assignment or update returns HTTP 409 and leaves the previous active profile/profile values unchanged. Reprocess or remove the existing corpus through an explicit migration workflow before changing embedding space.

## Secret handling

- `apiKey` is accepted on create/update but never returned by read APIs.
- Responses expose only configured/masked metadata plus profile revision.
- Omit `apiKey` on update to retain it; use the explicit clear flag to remove it.
- Do not place secrets in committed property files, examples, logs, or trace attributes.
- Runtime-settings responses mask provider keys and classify provider behavior as profile-managed.

Failures include 400 for invalid URLs/models/dimensions/tokenizer, 404 for unknown IDs, 409 for assigned-profile deletion or embedding incompatibility, and provider failures when an operation uses invalid/unreachable credentials. See runtime Swagger for exhaustive DTO fields.

Implementation: `KnowledgeBaseController` and `AiProfileController`; lifecycle and
provider construction under `knowledgebase` and `ai`; embedding compatibility in
`ai.application.EmbeddingCompatibility` using immutable `ai.domain.EmbeddingTarget`.
Search reads public knowledge-base/profile facts through
`SearchKnowledgeBaseAccess`; it no longer uses an `EmbeddingSpacePolicy` bridge.
