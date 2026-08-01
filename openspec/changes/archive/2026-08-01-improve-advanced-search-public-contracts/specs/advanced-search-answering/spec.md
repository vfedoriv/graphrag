## ADDED Requirements

### Requirement: Versioned advanced-search results have an explicit public schema
The version-1 result API SHALL expose typed answer, confidence, limitation, claim, source-range, evidence, context, graph-fact, answer-diagnostic, and retrieval/ranking diagnostic structures while retaining `payloadVersion` as the payload evolution boundary.

#### Scenario: Completed result is documented
- **WHEN** generated OpenAPI describes a completed version-1 result
- **THEN** clients can discover every version-1 property, enum, collection item, and nullable field without interpreting an untyped JSON object

#### Scenario: Partial or abstaining result is returned
- **WHEN** a run is partial, has insufficient evidence, or cannot publish a validated answer
- **THEN** the same typed schema represents available evidence, limitations, diagnostics, and nullable optional values

#### Scenario: Unsupported stored payload version is read
- **WHEN** a result has a payload version not supported by the deployed API
- **THEN** the system fails explicitly instead of coercing the payload into the version-1 DTO

### Requirement: Citation evidence snapshots human-readable source metadata
Every newly persisted evidence and context entry SHALL snapshot its source document original filename, content type, and stable display label as immutable run-result citation metadata.

#### Scenario: Source document is later replaced or renamed
- **WHEN** a historical result is read after current document metadata has changed
- **THEN** its evidence retains the source filename, content type, and display label captured when the result was created

#### Scenario: Source document is later unavailable
- **WHEN** current document metadata cannot be resolved while reading a historical result
- **THEN** the persisted citation metadata remains available without joining the current document list

#### Scenario: Legacy result lacks source metadata
- **WHEN** a version-1 result persisted before this change is read
- **THEN** it remains readable with explicitly nullable source metadata fields

### Requirement: Result fixtures cover supported terminal answer shapes
Serialization and OpenAPI contract tests SHALL cover completed, partial, insufficient-evidence, and answer-unavailable version-1 results and SHALL validate citation and graph-fact referential integrity.

#### Scenario: Contract fixture is changed incompatibly
- **WHEN** a DTO or serializer change removes or changes a required version-1 property without changing the payload version
- **THEN** the contract test fails before publication
