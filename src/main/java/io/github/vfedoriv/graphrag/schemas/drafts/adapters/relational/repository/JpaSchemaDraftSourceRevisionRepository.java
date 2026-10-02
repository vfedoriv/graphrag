package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.repository;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceRevisionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaSchemaDraftSourceRevisionRepository
    extends JpaRepository<SchemaDraftSourceRevisionEntity, String> {
    List<SchemaDraftSourceRevisionEntity> findBySourceIdOrderByRevisionDesc(String sourceId);
}
