## ADDED Requirements

### Requirement: Advanced-search examples match the public request contract
Repository guidance and generated OpenAPI examples SHALL use the implemented `maximumEvidence` request property and SHALL describe the asynchronous advanced-search lifecycle without advertising the retired Hybrid Search endpoint.

#### Scenario: Documented submission example is serialized
- **WHEN** the advanced-search request example is exercised by a controller contract test
- **THEN** its evidence bound is read from `maximumEvidence` rather than silently falling back to a default

#### Scenario: Endpoint migration guidance is checked
- **WHEN** public API migration documentation is validated
- **THEN** it directs clients to advanced-search run creation, polling, and result resources and does not restore or advertise a Hybrid Search compatibility route
