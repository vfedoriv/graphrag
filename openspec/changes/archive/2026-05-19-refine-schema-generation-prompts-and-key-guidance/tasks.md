## 1. Prompt Contract Updates

- [x] 1.1 Update `LLMGraphTransformerExt.USER_TEMPLATE` required output example so `head_properties`, `relation_properties`, and `tail_properties` are shown as JSON key/value pairs with string values (remove array-shaped examples).
- [x] 1.2 Update surrounding USER_TEMPLATE wording to explicitly state these properties are key/value maps.
- [x] 1.3 Refine `SCHEMA_PROMPT_CONTRACT` text in `LangChain4jSchemaGenerationService` to include canonical-key heuristics and examples (product/model/person/location-style guidance), and allow node `key` to be a list of property names for composite identity.

## 2. Logic Alignment And Contract Enforcement

- [x] 2.1 Review and adjust schema generation logic to align with the new prompt contract for canonical key guidance, including composite key representation.
- [x] 2.2 Remove backward compatibility for array-valued property outputs from LLM and enforce strict scalar string values in schema-generation property maps.
- [x] 2.3 Ensure advisory warning behavior for key/property mismatch remains non-blocking and consistent with updated prompt semantics.
- [x] 2.4 Update/create schema validation and identity-building logic so composite key values can be consumed later for unique node key construction.

## 3. Tests

- [x] 3.1 Update/add prompt-focused tests to assert new USER_TEMPLATE property-map examples, canonical-key guidance wording, and composite key guidance are present.
- [x] 3.2 Update/add transformer/service tests validating array-valued property outputs are treated as invalid for schema-generation contract.
- [x] 3.3 Update/add schema generation warning and validator tests for both single-property and composite-property node keys.

## 4. Verification

- [x] 4.1 Run targeted tests for `LLMGraphTransformerExt` and schema generation service/controller.
- [x] 4.2 Run `./mvnw test`.
- [x] 4.3 Run `openspec validate refine-schema-generation-prompts-and-key-guidance --strict`.
