## ADDED Requirements

### Requirement: Reprocessing preparation respects document ownership
Architecture verification SHALL require schema-owned reprocessing preparation,
retry preparation, and document-specific target inspection to use consumer-owned
ports implemented through document public capabilities. Document repository,
processing-option, chunk-classification, and runtime chunker dependencies SHALL
remain behind document-owned implementations.

#### Scenario: A preparation workflow accesses document internals
- **WHEN** architecture verification finds a dependency from schema-owned reprocessing preparation or target inspection to document repositories, persistence records, option resolvers, processing stages, or chunker implementation services
- **THEN** verification fails and identifies the originating dependency

#### Scenario: A plan consumes prepared document values
- **WHEN** a schema-owned plan workflow consumes prepared document identities, source hashes, classification, and target values through its port
- **THEN** verification permits the public immutable contract dependency
- **AND** plan claims, selection policy, schema target decisions, and plan persistence remain schema-owned

### Requirement: Completed reprocessing isolation removes preparation exceptions
Architecture verification SHALL remove the reprocessing preparation exceptions
retained during execution/recovery isolation. Integration adapters SHALL map
consumer and provider contracts without depending on either feature's
repositories or moving business workflow ownership into application assembly.

#### Scenario: A prior preparation exception remains after migration
- **WHEN** architecture verification evaluates completed reprocessing isolation
- **THEN** no preparation exception permits access to document internals from schema-owned reprocessing code

#### Scenario: An integration adapter bypasses public capabilities
- **WHEN** an integration adapter directly accesses a document repository or implements document classification or schema plan policy
- **THEN** boundary verification rejects the adapter dependency or focused adapter verification identifies the misplaced behavior
