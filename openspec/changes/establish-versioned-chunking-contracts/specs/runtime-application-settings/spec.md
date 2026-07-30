## ADDED Requirements

### Requirement: Typed versioned chunking settings
The runtime settings catalog SHALL expose allowlisted strategy, target-token, overlap-token, and hard character-limit settings with validation, live-apply metadata, typed accessors, and the resulting effective chunker revision.

#### Scenario: Invalid overlap
- **WHEN** an update makes overlap negative or not smaller than the applicable target
- **THEN** the settings API rejects the update atomically

#### Scenario: Live chunking update
- **WHEN** a valid mutable chunking setting changes
- **THEN** the saved value applies to subsequent processing and the response states that previously processed documents retain their snapshotted revision until reprocessed

#### Scenario: Compatibility alias
- **WHEN** a legacy chunking key remains configured during the compatibility period
- **THEN** the catalog resolves it according to documented precedence and reports one typed effective value
