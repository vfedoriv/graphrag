## ADDED Requirements

### Requirement: Reprocessing execution and recovery respect document ownership
Architecture verification SHALL require schema-owned reprocessing execution and
document-outcome inspection to use consumer-owned ports. Document execution,
source inspection, and processing-run retrieval SHALL reside behind
document-owned public capabilities. Integration adapters SHALL translate between
those contracts without introducing a schemas-to-documents implementation
dependency or a feature-to-assembly dependency.

#### Scenario: A migrated execution component accesses document internals
- **WHEN** architecture verification finds a dependency from migrated reprocessing execution or recovery code to document repositories, persistence records, processing stages, or document implementation services
- **THEN** verification fails and identifies the originating dependency

#### Scenario: Integration connects the features
- **WHEN** an application integration adapter implements a schema-owned execution or outcome port
- **THEN** it may use document public capabilities and boundary values
- **AND** neither feature depends on the integration adapter implementation

### Requirement: Reprocessing preparation exceptions are bounded during migration
Architecture verification SHALL keep preparation dependencies remaining outside
the migrated execution/recovery components explicitly named and frozen. An
exception for preparation SHALL NOT authorize document-internal dependencies in
execution or recovery, new callers, or additional preparation dependencies.

#### Scenario: Existing preparation access remains temporarily
- **WHEN** verification encounters a named pre-existing dependency used only by reprocessing input preparation
- **THEN** it is accepted as a documented transitional exception with its removal assigned to the preparation isolation change

#### Scenario: A transitional dependency expands
- **WHEN** a new dependency or migrated execution/recovery caller attempts to use that exception
- **THEN** architecture verification fails
