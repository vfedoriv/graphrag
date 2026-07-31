## REMOVED Requirements

### Requirement: Hybrid search endpoint is exposed
**Reason**: Replaced by the durable advanced-search run API.
**Migration**: Submit `POST /api/v1/knowledge-bases/{knowledgeBaseId}/queries/advanced-search-runs` and poll its status and result routes.

### Requirement: Search query is embedded for vector retrieval
**Reason**: Dense embedding is now one optional advanced-search retrieval branch.
**Migration**: Use advanced search; embedding compatibility is reported through run diagnostics and failure behavior.

### Requirement: Vector hits are scoped to the knowledge base
**Reason**: Advanced-search text retrieval owns stronger equivalent isolation.
**Migration**: Use advanced-search evidence results.

### Requirement: Hybrid search returns ranked chunk evidence
**Reason**: Advanced search returns fused/reranked evidence plus an answer and citations.
**Migration**: Read `AdvancedSearchEvidence` from the completed or partial run result.

### Requirement: Hybrid search includes graph context
**Reason**: Evidence-backed structured graph retrieval replaces `MENTIONS` expansion.
**Migration**: Read supporting graph facts and extraction-parent citations from advanced-search results.

### Requirement: Hybrid search request bounds are enforced
**Reason**: Advanced-search run and settings contracts define the replacement bounds.
**Migration**: Use `maxEvidence`, `includeEvidenceText`, and the typed advanced-search settings.

### Requirement: Hybrid search scopes candidates within Neo4j
**Reason**: Advanced retrieval branches scope candidates before branch limits.
**Migration**: Use the advanced-search run API; no client action beyond endpoint migration is required.

### Requirement: Hybrid search enriches metadata relationally in batches
**Reason**: Advanced-search candidate assembly performs scoped relational enrichment.
**Migration**: Consume source metadata in advanced-search evidence.

### Requirement: Contextual-vector source-safe retrieval
**Reason**: Advanced-search text retrieval preserves contextual-vector and source-text separation.
**Migration**: Use advanced-search evidence and citations.

### Requirement: Separate hybrid evidence and context
**Reason**: Advanced-search ranking and answering use typed child evidence, graph parents, and context-only parents.
**Migration**: Map legacy hit evidence/context fields to the corresponding advanced-search evidence fields.
