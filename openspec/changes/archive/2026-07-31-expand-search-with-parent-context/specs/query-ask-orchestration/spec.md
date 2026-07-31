## ADDED Requirements

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
