## ADDED Requirements

### Requirement: Public parent citation for graph evidence
Public graph-fact evidence SHALL expose the authoritative extraction parent ID, permitted parent text or excerpt, structural path, page range, processing run, and strategy revision and SHALL NOT project child citations for that fact.

#### Scenario: Cross-page extraction parent
- **WHEN** a graph fact was extracted from an accepted cross-page parent
- **THEN** its public evidence identifies the bounded parent page range as one graph citation
