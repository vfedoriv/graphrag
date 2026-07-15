## ADDED Requirements

### Requirement: Analysis source outcomes use the standard page envelope
The system SHALL represent the selected source-outcome slice in an analysis status response as a typed page envelope with zero-based page number, bounded page size, total element count, and ordered content.

#### Scenario: Client polls a paged analysis status
- **WHEN** a client requests an analysis run with page and size parameters
- **THEN** the response includes the requested bounded source-outcome page as `page`, `size`, `totalElements`, and `content`
- **AND** aggregate run status and counts describe the entire run rather than only the selected page

#### Scenario: Requested page has no outcomes
- **WHEN** the requested page is beyond the available source outcomes
- **THEN** the response returns empty content with the stable page metadata and full total element count
