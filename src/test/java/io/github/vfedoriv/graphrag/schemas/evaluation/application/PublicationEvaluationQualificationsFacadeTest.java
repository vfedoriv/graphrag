package io.github.vfedoriv.graphrag.schemas.evaluation.application;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.configuration.SchemaDraftEvaluationProperties;
import io.github.vfedoriv.graphrag.schemas.evaluation.domain.*;
import io.github.vfedoriv.graphrag.schemas.evaluation.ports.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class PublicationEvaluationQualificationsFacadeTest {
    @Test void selectsFirstCompletedOrPartialExactRevisionAndProjectionWithoutApplyingThreshold() {
        SchemaDraftEvaluationRunRepository runs = mock(SchemaDraftEvaluationRunRepository.class);
        PublicationEvaluationQualificationsFacade facade = new PublicationEvaluationQualificationsFacade(runs,
            new SchemaDraftEvaluationProperties(1, 1, 3, true, false));
        when(runs.findByDraftIdOrderByCreatedAtDesc("draft")).thenReturn(List.of(
            run("failed", 2, "hash", SchemaDraftEvaluationStatus.FAILED, 8),
            run("wrong-revision", 3, "hash", SchemaDraftEvaluationStatus.COMPLETED, 8),
            run("wrong-hash", 2, "other", SchemaDraftEvaluationStatus.COMPLETED, 8),
            run("partial", 2, "hash", SchemaDraftEvaluationStatus.PARTIAL, 1),
            run("completed", 2, "hash", SchemaDraftEvaluationStatus.COMPLETED, 8)));
        assertThat(facade.requirement().requiredForPublication()).isTrue();
        assertThat(facade.requirement().minimumSuccessfulDocuments()).isEqualTo(3);
        assertThat(facade.qualifying("draft", 2, "hash")).hasValueSatisfying(value -> {
            assertThat(value.id()).isEqualTo("partial"); assertThat(value.succeededDocuments()).isEqualTo(1);
        });
        assertThat(facade.qualifying("draft", 2, "missing")).isEmpty();
    }
    private SchemaDraftEvaluationRunNode run(String id, long revision, String hash, SchemaDraftEvaluationStatus status, int succeeded) {
        SchemaDraftEvaluationRunNode run = new SchemaDraftEvaluationRunNode(); run.setId(id); run.setDraftRevision(revision);
        run.setProjectionContentHash(hash); run.setStatus(status); run.setSucceededDocuments(succeeded); return run;
    }
}
