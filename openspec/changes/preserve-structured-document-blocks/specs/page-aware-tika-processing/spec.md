## ADDED Requirements

### Requirement: Classified PDF structure
The PDF parser SHALL treat page boundary and page text order as authoritative, expose paragraph and line boundaries only as layout-derived hints, and SHALL NOT recognize PDF headings, lists, tables, or code blocks without a future fixture-backed contract.

#### Scenario: Multi-page PDF
- **WHEN** Tika returns ordered page containers with layout-derived inner blocks
- **THEN** page number and order are authoritative while inner paragraph and line boundaries are marked as hints

#### Scenario: Ambiguous styled text
- **WHEN** PDF text visually resembles a heading, list, table, or code block
- **THEN** the parser preserves it as text without assigning one of those authoritative block kinds
