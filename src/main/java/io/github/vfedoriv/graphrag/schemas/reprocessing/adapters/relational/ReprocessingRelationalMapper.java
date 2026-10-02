package io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingItemEntity;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingPlanEntity;
import org.springframework.beans.BeanUtils;

final class ReprocessingRelationalMapper {
    private ReprocessingRelationalMapper() { }

    static SchemaReprocessingPlanNode toDomain(SchemaReprocessingPlanEntity source) {
        return copy(source, new SchemaReprocessingPlanNode());
    }

    static SchemaReprocessingPlanEntity toEntity(SchemaReprocessingPlanNode source) {
        return copy(source, new SchemaReprocessingPlanEntity());
    }

    static SchemaReprocessingItemNode toDomain(SchemaReprocessingItemEntity source) {
        return copy(source, new SchemaReprocessingItemNode());
    }

    static SchemaReprocessingItemEntity toEntity(SchemaReprocessingItemNode source) {
        return copy(source, new SchemaReprocessingItemEntity());
    }

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
