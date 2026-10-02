# Knowledge bases and AI profiles

A knowledge base is the ownership boundary for schemas, documents, retrieval, and queries. Its ID is supplied by the client. Each knowledge base has one active AI profile, and new knowledge bases inherit the persisted default profile.

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
    "defaultProfile":true
  }'
```

`POST/GET /ai-profiles`, `GET/PUT/DELETE /ai-profiles/{profileId}` provide CRUD. Setting a new default affects knowledge bases created later; it does not silently reassign existing knowledge bases.

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
