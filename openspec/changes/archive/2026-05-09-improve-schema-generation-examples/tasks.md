## 1. API Contract

- [x] 1.1 Update schema generation request/response DTOs so text generation requires `example`, removes `save`, and no longer exposes a saved schema identifier.
- [x] 1.2 Add DTOs for example generation requests and responses, including required `text`, optional `userPrompt`, and returned `example`.
- [x] 1.3 Update `SchemaController` OpenAPI annotations and method signatures for `/schemas/generate` and `/schemas/generate/from-file`.
- [x] 1.4 Add controller methods for `/schemas/generate/example` and `/schemas/generate/example/from-file`.

## 2. Service Implementation

- [x] 2.1 Extend `SchemaGenerationService` with `generateYaml(name, version, description, text, example)` and `generateExample(text, userPrompt)`.
- [x] 2.2 Update `LangChain4jSchemaGenerationService` to pass the caller-provided example into `LLMGraphTransformer`, verifying the exact LangChain4j 1.14 API against the local dependency.
- [x] 2.3 Implement LLM prompt/model logic for generating a reviewable example containing representative entities and relationships from source text plus optional `userPrompt`.
- [x] 2.4 Remove schema registry persistence from generation flows and delete controller helper code that saved generated schemas.

## 3. Validation and File Handling

- [x] 3.1 Add validation for non-blank `example` on text and file schema generation requests.
- [x] 3.2 Add validation for non-blank `text` on example generation requests.
- [x] 3.3 Reuse existing file parsing for both file-based schema generation and file-based example generation, preserving invalid file error behavior.

## 4. Tests and Verification

- [x] 4.1 Update existing `SchemaControllerTest` coverage for schema generation with required examples and no save behavior.
- [x] 4.2 Add controller/service tests for text and file example generation, including `userPrompt` propagation.
- [x] 4.3 Add negative tests for missing or blank examples, missing or blank example-generation text, and removed save behavior.
- [x] 4.4 Run `./mvnw test` and fix regressions.
