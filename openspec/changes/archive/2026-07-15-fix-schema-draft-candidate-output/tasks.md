## 1. Portable Candidate Output

- [x] 1.1 Simplify `DiscoverySourceAnalyzer` and `CandidateExtractionModelAdapter` to build and send only the prompt containing `BeanOutputConverter.getFormat()` through a plain Spring AI `Prompt`.
- [x] 1.2 Remove `OpenAiChatModel` detection, native `OpenAiChatOptions` construction, the native prompt argument, and related unused imports while continuing to convert normal assistant content through `BeanOutputConverter`.
- [x] 1.3 Make missing response results, missing messages, and blank normal assistant content fail with explicit content-safe exceptions; do not read reasoning metadata as candidate output.
- [x] 1.4 Increment the candidate prompt contract revision used by source-result reuse keys so results produced under native and portable request contracts are not conflated.

## 2. Failure Progress and Diagnostics

- [x] 2.1 Retain the prepared source chunk count across per-source candidate analysis and persist that count when model invocation, conversion, or candidate validation fails after preparation.
- [x] 2.2 Preserve a zero chunk count for failures that occur before source preparation completes.
- [x] 2.3 Extend the per-source failure warning with prepared chunk count, `LogMetadata.exceptionType`, and `LogMetadata.exceptionMessageFingerprint` while keeping all source, prompt, response, reasoning, and candidate content out of normal logs.

## 3. Regression Coverage and Verification

- [x] 3.1 Update adapter tests to assert that portable format instructions are used for OpenAI-compatible and generic chat models and that valid normal assistant JSON converts successfully.
- [x] 3.2 Add adapter tests for missing result/message, blank content with populated reasoning metadata, malformed normal-content JSON, and the rule that reasoning metadata is never treated as final output.
- [x] 3.3 Add analysis-service coverage proving failed outcomes retain prepared chunk counts, pre-preparation failures retain zero, and emitted diagnostics contain only approved metadata.
- [x] 3.4 Run the focused candidate-extraction and draft-analysis tests, then run `./mvnw test`.
- [x] 3.5 Run `graphify update .` after implementation and confirm the change remains consistent with the updated OpenSpec requirements.
