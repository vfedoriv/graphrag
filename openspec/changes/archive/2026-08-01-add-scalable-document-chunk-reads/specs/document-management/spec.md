## ADDED Requirements

### Requirement: Document chunk reads support bounded filtered pages
The system SHALL expose a typed page of chunks for an owned document with optional chunk-kind, parent-identifier, and section-index filters applied before total calculation and page selection.

#### Scenario: Client pages a large document
- **WHEN** a client requests a valid chunk page
- **THEN** the response contains zero-based page metadata, bounded size, matching total count, and full chunk representations ordered by `chunkIndex` ascending with deterministic identifier ties

#### Scenario: Parent children are requested
- **WHEN** a client filters by an owned parent chunk identifier
- **THEN** only children of that parent are counted and returned in deterministic document order

#### Scenario: Filters produce no matches
- **WHEN** valid filters match no chunks
- **THEN** the response contains an empty page and `totalElements=0`

#### Scenario: Paging or filter input is invalid
- **WHEN** page, size, kind, parent, or section input violates the documented bounds or combinations
- **THEN** the system returns RFC 7807 `400 Bad Request`

### Requirement: Document chunks support direct owned lookup
The system SHALL expose direct chunk lookup scoped by both document identifier and chunk identifier and SHALL return the same authoritative text and provenance fields used by collection reads.

#### Scenario: Citation chunk is opened
- **WHEN** a client requests a chunk that belongs to the owned document
- **THEN** the chunk is returned without loading the document's complete hierarchy

#### Scenario: Chunk belongs to another document
- **WHEN** the supplied chunk identifier exists under a different document
- **THEN** the system returns `404` without disclosing the foreign chunk

#### Scenario: Document does not exist
- **WHEN** direct lookup names a missing or inaccessible document
- **THEN** the system returns the established ownership-safe `404` response

### Requirement: Document hierarchy has a metadata-only summary
The system SHALL expose a bounded parent-hierarchy summary containing parent identity, deterministic order, provenance, revision metadata, structural scope, and child count without returning parent or child text.

#### Scenario: Hierarchical document is summarized
- **WHEN** a client requests the hierarchy summary for a recursively processed document
- **THEN** it receives a page of parent summaries and can use parent identifiers to retrieve children through the paginated chunk resource

#### Scenario: Flat document is summarized
- **WHEN** a processed document contains no parent chunks
- **THEN** the response contains no parent summaries and reports the flat chunk count without downloading chunk text

### Requirement: Complete-list chunk reads remain compatible
The existing complete-list chunk route SHALL retain its response envelope, full representations, and deterministic ordering until a separate breaking change explicitly removes it.

#### Scenario: Existing client calls the legacy route
- **WHEN** no new bounded subresource is requested
- **THEN** the client receives the same complete chunk list contract as before this change

#### Scenario: Future removal is considered
- **WHEN** maintainers decide to remove the complete-list route
- **THEN** removal requires a separate compatibility decision and migration guidance rather than occurring silently in this change
