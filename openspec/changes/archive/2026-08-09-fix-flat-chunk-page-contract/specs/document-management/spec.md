## MODIFIED Requirements

### Requirement: Document chunk reads support bounded filtered pages

The system SHALL expose a typed page of chunks for an owned document with optional chunk-kind, parent-identifier, and section-index filters applied before total calculation and page selection. The bounded page kind selector SHALL accept omitted, `PARENT`, `CHILD`, and virtual `FLAT` values case-insensitively. `FLAT` SHALL select only persisted `CHILD` chunks whose `parentChunkId` is null, while returned chunk records SHALL retain their persisted `kind=CHILD` value.

#### Scenario: Client pages a large document

- **WHEN** a client requests a valid chunk page
- **THEN** the response contains zero-based page metadata, bounded size, matching total count, and full chunk representations ordered by `chunkIndex` ascending with deterministic identifier ties

#### Scenario: Parent children are requested

- **WHEN** a client filters by an owned parent chunk identifier
- **THEN** only children of that parent are counted and returned in deterministic document order

#### Scenario: Flat chunks are requested

- **WHEN** a client requests `kind=FLAT` for an owned document
- **THEN** only chunks satisfying `kind=CHILD AND parentChunkId IS NULL` are counted and returned
- **AND** parent chunks and children with a non-null `parentChunkId` are excluded
- **AND** each returned record retains `kind=CHILD` and does not expose `kind=FLAT`

#### Scenario: Flat and hierarchical populations are mixed

- **WHEN** a document contains parent chunks, parented child chunks, and unparented child chunks
- **THEN** a `kind=FLAT` page returns only the unparented child population
- **AND** a `kind=CHILD` page continues to include both parented and unparented persisted child chunks

#### Scenario: Flat pages are combined with a section filter

- **WHEN** a client requests `kind=FLAT` with a valid `sectionIndex`
- **THEN** the section predicate is applied to the flat population before total calculation and page selection

#### Scenario: Flat page totals match hierarchy flat counts

- **WHEN** a client reads a stable document snapshot through the hierarchy endpoint and the bounded page endpoint
- **THEN** `flatChunkCount` equals `totalElements` for `kind=FLAT`
- **AND** both values use the predicate `documentId = requested document AND kind = CHILD AND parentChunkId IS NULL`

#### Scenario: Filters produce no matches

- **WHEN** valid filters match no chunks
- **THEN** the response contains an empty page and `totalElements=0`

#### Scenario: Flat and parent filters contradict each other

- **WHEN** a client requests `kind=FLAT` with a nonblank `parentChunkId`
- **THEN** the system returns RFC 7807 `400 Bad Request`
- **AND** the explanation states `parentChunkId cannot be used with kind=FLAT`
- **AND** no document ownership or graph page lookup is performed

#### Scenario: Paging or filter input is invalid

- **WHEN** page, size, kind, parent, or section input violates the documented bounds or combinations
- **THEN** the system returns RFC 7807 `400 Bad Request`

### Requirement: Document hierarchy has a metadata-only summary

The system SHALL expose a bounded parent-hierarchy summary containing parent identity, deterministic order, provenance, revision metadata, structural scope, and child count without returning parent or child text. Its `flatChunkCount` SHALL count only persisted `CHILD` chunks for the document whose `parentChunkId` is null, using the same logical population as `kind=FLAT` bounded pages.

#### Scenario: Hierarchical document is summarized

- **WHEN** a client requests the hierarchy summary for a recursively processed owned document
- **THEN** it receives a page of parent summaries and can use parent identifiers to retrieve children through the paginated chunk resource

#### Scenario: Flat document is summarized

- **WHEN** a processed document contains no parent chunks
- **THEN** the response contains no parent summaries and reports the count of persisted unparented `CHILD` chunks without downloading chunk text

#### Scenario: Mixed document flat count excludes parented children

- **WHEN** a processed document contains both parented and unparented persisted `CHILD` chunks
- **THEN** `flatChunkCount` includes only the unparented persisted `CHILD` chunks
- **AND** the value equals the `totalElements` returned by the corresponding `kind=FLAT` page query for the stable snapshot
