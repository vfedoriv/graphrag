package io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational;

import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftAggregateRevisionEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftAnalysisRunEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftConflictEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftDecisionEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceResultEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftSourceRevisionEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.adapters.relational.entity.SchemaDraftStorageMutationEntity;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAggregateRevisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftAnalysisRunNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftConflictNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftDecisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceResultNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftSourceRevisionNode;
import io.github.vfedoriv.graphrag.schemas.drafts.domain.SchemaDraftStorageMutationNode;
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
