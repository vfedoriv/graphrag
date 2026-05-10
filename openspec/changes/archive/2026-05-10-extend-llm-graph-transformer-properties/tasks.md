## 1. Transformer Extension

- [x] 1.1 Inspect current LangChain4j `LLMGraphTransformer` usage and identify where node/edge properties are lost.
- [x] 1.2 Implement `LLMGraphTransformerExt` extending `LLMGraphTransformer` with overridden `transform()` that correctly maps node and edge properties from model output.
- [x] 1.3 Reuse/align property-handling rules using examples from `LLMGraphTransformer_python_example` and document key mapping assumptions.

## 2. Service Integration

- [x] 2.1 Update schema generation flow to instantiate and use `LLMGraphTransformerExt` instead of the base transformer.
- [x] 2.2 Ensure existing schema inference logic receives populated `GraphNode.properties` and `GraphEdge.properties` and continues to handle missing properties safely.

## 3. Tests and Verification

- [x] 3.1 Add unit tests for `LLMGraphTransformerExt` covering node properties present, edge properties present, and missing-properties cases.
- [x] 3.2 Add or update schema generation tests that verify transformed graph properties influence generated schema output as expected.
- [x] 3.3 Run targeted tests for transformer and schema generation, then run full `./mvnw test`.
