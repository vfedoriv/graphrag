package io.github.vfedoriv.graphrag.schemas.drafts.ports;

import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceRevisionNode;
import java.util.List;

public interface SchemaDraftSourceRevisionRepository {
    List<SchemaDraftSourceRevisionNode> findBySourceIdOrderByRevisionDesc(String sourceId);
    SchemaDraftSourceRevisionNode save(SchemaDraftSourceRevisionNode revision);
}
