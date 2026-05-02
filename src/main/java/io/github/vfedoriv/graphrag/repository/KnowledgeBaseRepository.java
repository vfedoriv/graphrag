package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.KnowledgeBaseNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface KnowledgeBaseRepository extends Neo4jRepository<KnowledgeBaseNode, String> {
}
