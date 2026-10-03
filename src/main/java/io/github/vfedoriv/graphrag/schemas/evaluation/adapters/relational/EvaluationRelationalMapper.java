package io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationOutcomeNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.SchemaDraftEvaluationRunNode;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationOutcomeEntity;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationRunEntity;
import org.springframework.beans.BeanUtils;

final class EvaluationRelationalMapper {
    private EvaluationRelationalMapper() {
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

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
