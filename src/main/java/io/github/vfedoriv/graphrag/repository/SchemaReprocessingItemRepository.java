package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface SchemaReprocessingItemRepository extends Neo4jRepository<SchemaReprocessingItemNode, String> {
    Page<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(String planId, Pageable pageable);
    java.util.List<SchemaReprocessingItemNode> findByPlanIdOrderByDocumentIdAsc(String planId);
}
