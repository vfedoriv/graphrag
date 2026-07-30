## ADDED Requirements

### Requirement: Citation-safe expanded synthesis
Ask orchestration SHALL use bounded expanded context for synthesis while returning child citations for text-retrieval evidence and parent citations only for graph evidence extracted from that parent.

#### Scenario: Text claim uses parent context
- **WHEN** a text-retrieval child is expanded to a parent for synthesis
- **THEN** the supported text claim cites the precise child rather than presenting the full parent as retrieved evidence

#### Scenario: Graph claim
- **WHEN** a claim is supported by graph evidence
- **THEN** the claim cites the authoritative extraction parent recorded by that evidence
