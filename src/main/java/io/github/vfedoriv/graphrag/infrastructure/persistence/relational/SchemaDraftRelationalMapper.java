package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftPublicationNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingItemNode;
import io.github.vfedoriv.graphrag.domain.SchemaReprocessingPlanNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEvaluationOutcomeEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEvaluationRunEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftPublicationEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaReprocessingItemEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaReprocessingPlanEntity;
import org.springframework.beans.BeanUtils;

final class SchemaDraftRelationalMapper {
    private SchemaDraftRelationalMapper() {
    }

    static SchemaDraftEvaluationRunNode toDomain(SchemaDraftEvaluationRunEntity source) {
        return copy(source, new SchemaDraftEvaluationRunNode());
    }

    static SchemaDraftEvaluationRunEntity toEntity(SchemaDraftEvaluationRunNode source) {
        return copy(source, new SchemaDraftEvaluationRunEntity());
    }

    static SchemaDraftEvaluationOutcomeNode toDomain(SchemaDraftEvaluationOutcomeEntity source) {
        return copy(source, new SchemaDraftEvaluationOutcomeNode());
    }

    static SchemaDraftEvaluationOutcomeEntity toEntity(SchemaDraftEvaluationOutcomeNode source) {
        return copy(source, new SchemaDraftEvaluationOutcomeEntity());
    }

    static SchemaDraftPublicationNode toDomain(SchemaDraftPublicationEntity source) {
        return copy(source, new SchemaDraftPublicationNode());
    }

    static SchemaDraftPublicationEntity toEntity(SchemaDraftPublicationNode source) {
        return copy(source, new SchemaDraftPublicationEntity());
    }

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
