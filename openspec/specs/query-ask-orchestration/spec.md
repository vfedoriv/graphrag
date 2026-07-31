# query-ask-orchestration Specification

## Purpose
TBD - created by archiving change move-query-ask-orchestration. Update Purpose after archive.
## Requirements
### Requirement: Ask workflow is owned by an application service
The system SHALL provide an application service that owns the one-shot natural-language ask workflow independently of the HTTP controller.

#### Scenario: Ask request succeeds
- **WHEN** the ask workflow receives a knowledge base identifier and natural-language prompt that generates valid Cypher
- **THEN** the workflow generates the query, executes the validated query, and returns a response containing both generation and execution results

### Requirement: Invalid generated queries are rejected before execution
The ask workflow SHALL reject invalid generated queries before execution.

#### Scenario: Generated query fails validation
- **WHEN** query generation returns validation errors
- **THEN** the ask workflow raises the existing query rejection behavior and does not call query execution

### Requirement: Ask workflow observability is preserved
The ask workflow SHALL preserve the existing query workflow observation name and high-cardinality attributes currently emitted by the one-shot query flow.

#### Scenario: Ask workflow records observation attributes
- **WHEN** the ask workflow runs
- **THEN** it records prompt length, validation validity, validation error count, generated Cypher length, and execution metrics when execution occurs

### Requirement: Query controller delegates ask orchestration
The query controller SHALL delegate one-shot ask orchestration to the application service and remain responsible only for HTTP request/response adaptation and request logging.

#### Scenario: Controller handles ask request
- **WHEN** the `/queries/ask` endpoint receives a valid request body
- **THEN** the controller delegates to the ask application service and returns its response

### Requirement: Citation-safe synthesis-ready evidence assembly
The reusable query evidence assembly boundary SHALL expose bounded expanded context for downstream synthesis while retaining child citations for text-retrieval evidence and parent citations only for graph evidence extracted from that parent. This requirement SHALL NOT add answer generation to or otherwise change the current `/queries/ask` endpoint.

#### Scenario: Text claim uses parent context
- **WHEN** a downstream synthesis consumer uses parent context expanded from a text-retrieval child
- **THEN** the supported text claim cites the precise child rather than presenting the full parent as retrieved evidence

#### Scenario: Graph claim
- **WHEN** a claim is supported by graph evidence
- **THEN** the claim cites the authoritative extraction parent recorded by that evidence

#### Scenario: Existing ask endpoint
- **WHEN** synthesis-ready evidence assembly is introduced
- **THEN** the current `/queries/ask` endpoint retains its existing query-generation and execution behavior
- **AND** no answer-generation stage is added
