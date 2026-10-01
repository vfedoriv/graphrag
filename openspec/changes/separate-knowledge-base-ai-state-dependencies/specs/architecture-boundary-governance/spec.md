## ADDED Requirements

### Requirement: Knowledge-base lifecycle respects document ownership
Architecture verification SHALL require knowledge-base lifecycle document-state inspection and artifact cleanup to use knowledge-base-owned ports implemented through document public capabilities. Document repositories, persistence records, and cleanup implementations SHALL remain behind document-owned capabilities; lifecycle admission and sequencing SHALL remain knowledge-base-owned.

#### Scenario: Lifecycle directly accesses document internals
- **WHEN** verification finds a migrated knowledge-base lifecycle dependency on document repositories, persistence records, or artifact cleanup implementations
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Lifecycle requests owned facts and cleanup
- **WHEN** knowledge-base lifecycle consumes an owned-document count or requests scoped artifact cleanup through its ports
- **THEN** verification permits the public immutable contract dependency
- **AND** the integration implementation accesses only document public capabilities rather than repositories or cleanup implementations

### Requirement: AI embedding compatibility separates observations from rules
Architecture verification SHALL require stored embedding observations to enter AI compatibility through an AI-owned port backed by a document public capability. Compatibility rules SHALL operate on immutable non-secret target and stored-observation values without document persistence types, repository access, external clients, or application assembly dependencies.

#### Scenario: Compatibility reads stored chunk records directly
- **WHEN** verification finds document repository or persistence-record dependencies in migrated AI compatibility code
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Deterministic comparison uses boundary values
- **WHEN** compatibility compares a target embedding descriptor with stored observations supplied through its port
- **THEN** verification permits the immutable value dependencies
- **AND** rejects persistence or external-client dependencies in the deterministic rule

### Requirement: AI profile management respects knowledge-base assignment ownership
Architecture verification SHALL require AI profile update and deletion assignment inspection to use an AI-owned port implemented through a knowledge-base public capability. AI profile persistence SHALL NOT access knowledge-base repositories or persistence records; knowledge bases SHALL retain ownership of profile associations.

#### Scenario: AI profile persistence reads foreign assignment state
- **WHEN** verification finds knowledge-base repository or persistence-record dependencies in AI profile persistence
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Profile admission consumes assignment facts
- **WHEN** AI profile management checks assignment presence or assigned knowledge-base identities through its port
- **THEN** verification permits the public contract dependency
- **AND** knowledge-base persistence stays behind its public capability

### Requirement: Knowledge-base and AI integration boundaries are scoped and enforced
Architecture verification SHALL constrain the migrated integration adapters to consumer-owned ports, provider public capabilities, immutable boundary values, and application wiring support. Adapters SHALL NOT access feature repositories, persist state, or own lifecycle or compatibility decisions. Production features SHALL NOT depend on assembly implementations. Any transitional exceptions outside this migrated slice SHALL be frozen by explicit class/path with an identified retirement slice and SHALL NOT permit the foreign state dependencies removed by this change.

#### Scenario: An integration adapter bypasses a public capability
- **WHEN** verification finds a migrated integration adapter dependency on feature repositories, persistence records, or implementation services
- **THEN** verification fails and identifies the bypass

#### Scenario: A feature depends on application assembly
- **WHEN** verification finds a production feature dependency on a migrated integration adapter implementation
- **THEN** verification fails and identifies the dependency

#### Scenario: A transitional exception expands
- **WHEN** a new caller or forbidden dependency attempts to use a named transitional exception outside its frozen scope
- **THEN** verification rejects the dependency
- **AND** completed reprocessing isolation remains enforced without preparation exceptions
