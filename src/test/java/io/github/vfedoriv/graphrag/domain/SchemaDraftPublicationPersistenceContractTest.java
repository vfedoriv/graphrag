package io.github.vfedoriv.graphrag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationOutcomeEntity;
import io.github.vfedoriv.graphrag.schemas.evaluation.adapters.relational.entity.SchemaDraftEvaluationRunEntity;
import io.github.vfedoriv.graphrag.schemas.publication.adapters.relational.entity.SchemaDraftPublicationEntity;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingItemEntity;
import io.github.vfedoriv.graphrag.schemas.reprocessing.adapters.relational.entity.SchemaReprocessingPlanEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaDraftPublicationPersistenceContractTest {
    @Test
    void relationalEntitiesExposeStableIdsAndOptimisticVersions() {
        List<Class<?>> entities = List.of(
            SchemaDraftEvaluationRunEntity.class,
            SchemaDraftEvaluationOutcomeEntity.class,
            SchemaDraftPublicationEntity.class,
            SchemaReprocessingPlanEntity.class,
            SchemaReprocessingItemEntity.class
        );

        for (Class<?> entity : entities) {
            assertThat(entity.getAnnotation(Entity.class)).as(entity.getSimpleName()).isNotNull();
            assertThat(fieldWith(entity, Id.class)).as(entity.getSimpleName() + " id").isNotNull();
            assertThat(fieldWith(entity, Version.class)).as(entity.getSimpleName() + " version").isNotNull();
        }
    }

    private Field fieldWith(Class<?> type, Class<? extends java.lang.annotation.Annotation> annotation) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation)) return field;
        }
        return null;
    }
}
