package io.github.vfedoriv.graphrag.infrastructure.persistence.relational;

import io.github.vfedoriv.graphrag.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.domain.SchemaDraftStorageMutationNode;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftAggregateRevisionEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftAnalysisRunEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftConflictEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftDecisionEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftSourceEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftSourceResultEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftSourceRevisionEntity;
import io.github.vfedoriv.graphrag.infrastructure.persistence.relational.entity.SchemaDraftStorageMutationEntity;
import org.springframework.beans.BeanUtils;

final class SchemaDraftRelationalMapper {
    private SchemaDraftRelationalMapper() {
    }

    static SchemaDraftNode toDomain(SchemaDraftEntity source) {
        return copy(source, new SchemaDraftNode());
    }

    static SchemaDraftEntity toEntity(SchemaDraftNode source) {
        return copy(source, new SchemaDraftEntity());
    }

    static SchemaDraftSourceNode toDomain(SchemaDraftSourceEntity source) {
        return copy(source, new SchemaDraftSourceNode());
    }

    static SchemaDraftSourceEntity toEntity(SchemaDraftSourceNode source) {
        return copy(source, new SchemaDraftSourceEntity());
    }

    static SchemaDraftSourceRevisionNode toDomain(SchemaDraftSourceRevisionEntity source) {
        return copy(source, new SchemaDraftSourceRevisionNode());
    }

    static SchemaDraftSourceRevisionEntity toEntity(SchemaDraftSourceRevisionNode source) {
        return copy(source, new SchemaDraftSourceRevisionEntity());
    }

    static SchemaDraftAnalysisRunNode toDomain(SchemaDraftAnalysisRunEntity source) {
        return copy(source, new SchemaDraftAnalysisRunNode());
    }

    static SchemaDraftAnalysisRunEntity toEntity(SchemaDraftAnalysisRunNode source) {
        return copy(source, new SchemaDraftAnalysisRunEntity());
    }

    static SchemaDraftSourceResultNode toDomain(SchemaDraftSourceResultEntity source) {
        return copy(source, new SchemaDraftSourceResultNode());
    }

    static SchemaDraftSourceResultEntity toEntity(SchemaDraftSourceResultNode source) {
        return copy(source, new SchemaDraftSourceResultEntity());
    }

    static SchemaDraftAggregateRevisionNode toDomain(SchemaDraftAggregateRevisionEntity source) {
        return copy(source, new SchemaDraftAggregateRevisionNode());
    }

    static SchemaDraftAggregateRevisionEntity toEntity(SchemaDraftAggregateRevisionNode source) {
        return copy(source, new SchemaDraftAggregateRevisionEntity());
    }

    static SchemaDraftConflictNode toDomain(SchemaDraftConflictEntity source) {
        return copy(source, new SchemaDraftConflictNode());
    }

    static SchemaDraftConflictEntity toEntity(SchemaDraftConflictNode source) {
        return copy(source, new SchemaDraftConflictEntity());
    }

    static SchemaDraftDecisionNode toDomain(SchemaDraftDecisionEntity source) {
        return copy(source, new SchemaDraftDecisionNode());
    }

    static SchemaDraftDecisionEntity toEntity(SchemaDraftDecisionNode source) {
        return copy(source, new SchemaDraftDecisionEntity());
    }

    static SchemaDraftStorageMutationNode toDomain(SchemaDraftStorageMutationEntity source) {
        return copy(source, new SchemaDraftStorageMutationNode());
    }

    static SchemaDraftStorageMutationEntity toEntity(SchemaDraftStorageMutationNode source) {
        return copy(source, new SchemaDraftStorageMutationEntity());
    }

    private static <T> T copy(Object source, T target) {
        BeanUtils.copyProperties(source, target);
        return target;
    }
}
