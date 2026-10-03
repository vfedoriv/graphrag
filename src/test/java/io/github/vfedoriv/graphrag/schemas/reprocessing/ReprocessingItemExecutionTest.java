package io.github.vfedoriv.graphrag.schemas.reprocessing;

import io.github.vfedoriv.graphrag.schemas.reprocessing.ports.ReprocessingDocumentExecutor;
import io.github.vfedoriv.graphrag.schemas.reprocessing.application.ReprocessingItemExecution;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReprocessingItemExecutionTest {
    @Test
    void preservesPortResultsAndExceptionClassWithoutContent() throws Exception {
        ReprocessingDocumentExecutor.Request request = new ReprocessingDocumentExecutor.Request(
            "kb", "doc", "hash", "profile", new ReprocessingDocumentExecutor.Activation(Map.of()));
        for (ReprocessingDocumentExecutor.Status status : ReprocessingDocumentExecutor.Status.values()) {
            ReprocessingDocumentExecutor.Result expected = new ReprocessingDocumentExecutor.Result(status, "category");
            ReprocessingDocumentExecutor port = new ReprocessingDocumentExecutor() {
                public boolean sourceMatches(Source source) { return true; }
                public Result execute(Request value) { assertThat(value).isSameAs(request); return expected; }
            };
            ReprocessingItemExecution collaborator = new ReprocessingItemExecution(port);
            assertThat(collaborator.execute(request)).isEqualTo(expected);
        }
        ReprocessingDocumentExecutor throwing = new ReprocessingDocumentExecutor() {
            public boolean sourceMatches(Source source) { throw new IllegalStateException("private content"); }
            public Result execute(Request value) { throw new IllegalStateException("private content"); }
        };
        ReprocessingItemExecution collaborator = new ReprocessingItemExecution(throwing);
        assertThat(collaborator.execute(request))
            .isEqualTo(new ReprocessingDocumentExecutor.Result(ReprocessingDocumentExecutor.Status.FAILED, "IllegalStateException"));
        assertThatThrownBy(() -> collaborator.sourceMatches(new ReprocessingDocumentExecutor.Source("kb", "doc", "hash")))
            .isInstanceOf(IllegalStateException.class);
    }
}
