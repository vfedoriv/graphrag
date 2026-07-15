package io.github.vfedoriv.graphrag.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.neo4j.core.schema.Node;

class SchemaDraftPublicationPersistenceContractTest {
    @Test
    void durableEntitiesExposeStableIdsLabelsAndOptimisticVersions() {
        List<Class<?>> entities = List.of(
            SchemaDraftEvaluationRunNode.class,
            SchemaDraftEvaluationOutcomeNode.class,
            SchemaDraftPublicationNode.class,
            SchemaReprocessingPlanNode.class,
            SchemaReprocessingItemNode.class
        );

        for (Class<?> entity : entities) {
            assertThat(entity.getAnnotation(Node.class)).as(entity.getSimpleName()).isNotNull();
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
