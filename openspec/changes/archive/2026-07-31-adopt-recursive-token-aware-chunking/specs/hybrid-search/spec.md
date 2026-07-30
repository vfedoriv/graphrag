## ADDED Requirements

### Requirement: Contextual-vector source-safe retrieval
Hybrid search SHALL query vectors generated from child `embeddingText` while returning and scoring authoritative child identity, `sourceText`, and structured metadata under the existing knowledge-base and embedding-space guards.

#### Scenario: Context term matches
- **WHEN** a contextual header term helps a child vector become a candidate
- **THEN** the hit may be returned but its public text excludes the synthetic header
