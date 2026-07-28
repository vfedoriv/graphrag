package io.github.vfedoriv.graphrag.repository;

import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import java.util.List;

public interface SchemaDraftSourceRevisionRepository {
    List<SchemaDraftSourceRevisionNode> findBySourceIdOrderByRevisionDesc(String sourceId);
    SchemaDraftSourceRevisionNode save(SchemaDraftSourceRevisionNode revision);
}
