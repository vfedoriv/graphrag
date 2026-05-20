## MODIFIED Requirements

### Requirement: Graph writes reject incomplete identity material
The system MUST NOT persist extracted graph nodes or relationships when required identity material is incomplete after extraction normalization and filtering.

#### Scenario: Node key component is blank before write
- **WHEN** graph persistence receives an extracted node that is missing any required key component for its schema node definition
- **THEN** graph persistence is rejected before deriving the node identifier

#### Scenario: Relationship endpoint key component is blank before write
- **WHEN** graph persistence receives an extracted relationship that is missing any required key component for the endpoint schema node definition
- **THEN** graph persistence is rejected before deriving endpoint or relationship identifiers

#### Scenario: Validation filters incomplete identity before write
- **WHEN** extraction validation receives model output with incomplete node or relationship identity material that cannot be repaired
- **THEN** validation omits the invalid element from the graph extraction result passed to persistence
- **AND** graph persistence receives only elements with complete identity material
