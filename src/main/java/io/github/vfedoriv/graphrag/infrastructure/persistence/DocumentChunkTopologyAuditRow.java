package io.github.vfedoriv.graphrag.infrastructure.persistence;

public record DocumentChunkTopologyAuditRow(
    String documentId,
    long chunkCount,
    long parentCount,
    long parentedChildCount,
    long unparentedChildCount,
    long orphanChildCount,
    long crossScopeChildCount,
    long unsupportedKindCount,
    long malformedParentCount,
    long missingHierarchyRelationshipCount,
    DocumentChunkTopology topology
) {

    public boolean invalid() {
        return topology == DocumentChunkTopology.INVALID;
    }
}
