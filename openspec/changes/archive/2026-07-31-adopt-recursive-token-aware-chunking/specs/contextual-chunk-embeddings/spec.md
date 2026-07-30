## ADDED Requirements

### Requirement: Separate source and embedding representations
Every embedded child SHALL retain exact `sourceText` and SHALL build a separate versioned `embeddingText` from available filename or title, format, structural path, page position, and source text in stable order.

#### Scenario: Partial context metadata
- **WHEN** one or more contextual fields are unavailable
- **THEN** the header omits those fields without empty placeholders or guessed values

#### Scenario: Embedding input limit
- **WHEN** header plus source text approaches an input limit
- **THEN** the bounded header and source chunk are constructed within both token and hard character guards

### Requirement: Synthetic context is not authoritative source
The system SHALL use contextual `embeddingText` only for dense embedding and SHALL use unmodified child `sourceText` plus separately indexed metadata for lexical retrieval, public evidence, and citations.

#### Scenario: Retrieved contextualized vector
- **WHEN** dense search matches a vector created from contextual embedding text
- **THEN** the returned evidence text and citation contain only authoritative source text and provenance
