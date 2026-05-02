package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.ExtractionRunNode;
import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ExtractionRunRepository extends Neo4jRepository<ExtractionRunNode, String> {

    List<ExtractionRunNode> findByDocumentIdOrderByStartedAtAsc(String documentId);
}
