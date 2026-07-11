## 1. Safe Operational Logging

- [x] 1.1 Inventory content-bearing application log sites and classify required metadata, correlation, and error fields.
- [x] 1.2 Replace preview-based normal logs with metadata-first safe events and rename misleading sanitization helpers.
- [x] 1.3 Restrict any local content diagnostics to explicit non-production configuration with bounded, auditable enablement.
- [x] 1.4 Verify application logs do not duplicate AI observation input/output capture behavior.

## 2. Test Signal Quality

- [x] 2.1 Add test-only logging configuration that reduces routine Testcontainers, Neo4j expected-schema, framework, and payload noise.
- [x] 2.2 Add captured-log tests proving recognizable document, prompt, query, and model-response data is absent from normal logs.
- [x] 2.3 Verify test failures and application warning/error diagnostics remain visible with the new configuration.

## 3. Documentation Alignment

- [x] 3.1 Synchronize Java, Spring Boot, Spring AI, and LangChain4j facts and links across README, AGENTS, CLAUDE, and `pom.xml`.
- [x] 3.2 Add a lightweight regression check for designated build-backed documentation facts and shared contributor guidance.
- [x] 3.3 Update operational guidance for trace capture versus application logging and run the full Maven suite.
