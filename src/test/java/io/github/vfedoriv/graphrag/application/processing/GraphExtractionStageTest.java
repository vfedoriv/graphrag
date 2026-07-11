package io.github.vfedoriv.graphrag.application.processing;

import static org.mockito.Mockito.verify;

import io.github.vfedoriv.graphrag.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.service.GraphExtractionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphExtractionStageTest {

    @Mock
    private GraphExtractionService graphExtractionService;

    @Test
    void delegatesExtractionWithPersistedChunksAndOverwritePolicy() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        DocumentChunkNode chunk = new DocumentChunkNode();
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");

        new GraphExtractionStage(graphExtractionService).execute(document, List.of(chunk), profile, true);

        verify(graphExtractionService).extract(document, List.of(chunk), true);
    }
}
