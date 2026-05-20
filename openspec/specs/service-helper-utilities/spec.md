# service-helper-utilities Specification

## Purpose
TBD - created by archiving change extract-service-helper-utilities. Update Purpose after archive.
## Requirements
### Requirement: Services delegate deterministic helper logic to cohesive support classes
The system SHALL keep service classes focused on orchestration by moving substantial deterministic helper logic into cohesive support classes with names and APIs aligned to their specific domain responsibility.

#### Scenario: Extraction validation helper logic is extracted
- **WHEN** graph extraction validation normalizes, repairs, filters, or matches extracted nodes and relationships
- **THEN** the deterministic helper behavior is implemented in one or more extraction-focused support classes instead of being hidden only in private service methods

#### Scenario: Cypher parsing helper logic is extracted
- **WHEN** Cypher validation extracts node labels, relationship types, type unions, or property references from query text
- **THEN** the deterministic parsing behavior is implemented in one or more Cypher-focused support classes instead of being hidden only in private service methods

#### Scenario: Graph write helper logic is extracted
- **WHEN** graph persistence derives stable identifiers, validates identity material, checks safe schema tokens, or filters declared properties
- **THEN** the deterministic helper behavior is implemented in one or more graph-write-focused support classes instead of being hidden only in private service methods

### Requirement: Service behavior remains externally unchanged
The system MUST preserve existing runtime behavior while helper logic is extracted from service classes.

#### Scenario: Public service contracts are preserved
- **WHEN** callers use existing service methods, controllers, or integration flows after the refactor
- **THEN** method behavior, response contracts, validation outcomes, exception types, and persistence effects remain compatible with the pre-refactor behavior

#### Scenario: Existing OpenSpec-backed behavior remains valid
- **WHEN** existing tests for graph extraction validation, Cypher validation, graph identity persistence, extraction cleanup, schema generation, and document processing are run
- **THEN** they continue to pass without weakening their assertions

### Requirement: Extracted support classes are directly unit tested
The system SHALL include focused unit tests for extracted support classes that cover the edge cases previously reachable only through service-level tests.

#### Scenario: Extraction helper tests cover filtering and repair
- **WHEN** extraction helper tests execute
- **THEN** they verify unknown label filtering, invalid relationship triple filtering, incomplete key handling, supported key repair, endpoint matching, and null or blank property handling

#### Scenario: Cypher helper tests cover reference parsing
- **WHEN** Cypher helper tests execute
- **THEN** they verify node label extraction, relationship union extraction, aliased relationship parsing, property-map exclusion, backtick trimming, and property reference extraction

#### Scenario: Graph write helper tests cover identity and property handling
- **WHEN** graph write helper tests execute
- **THEN** they verify stable identifier determinism, delimiter-safe identity canonicalization, incomplete identity rejection, safe token validation, and declared property filtering

### Requirement: Support classes avoid new side effects
Extracted support classes MUST NOT introduce new repository, Neo4j, external model, filesystem, or network side effects for logic that was previously deterministic private helper behavior.

#### Scenario: Pure helper receives explicit inputs
- **WHEN** a helper calculates validation decisions, parsed references, filtered properties, or stable identifiers
- **THEN** it uses explicit method inputs and returns values or structured outcomes without performing repository, Neo4j, filesystem, network, or model-client operations

#### Scenario: Services retain side-effect ownership
- **WHEN** a workflow requires repository access, Neo4j execution, model client calls, transaction-sensitive persistence, or lifecycle status updates
- **THEN** the service layer remains responsible for that workflow step after the refactor

