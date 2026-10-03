package io.github.vfedoriv.graphrag.documents.application.processing;

import static org.mockito.Mockito.verify;

import io.github.vfedoriv.graphrag.ai.profiles.domain.AiProfileNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentChunkNode;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
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

        new GraphExtractionStage(graphExtractionService).execute(document, List.of(chunk), profile.facts(), true);

        verify(graphExtractionService).extract(document, List.of(chunk), true);
    }

    @Test
    void selectsPersistedParentsAsAuthoritativeExtractionUnits() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("doc-1");
        DocumentChunkNode parent = new DocumentChunkNode();
        parent.setKind("PARENT");
        DocumentChunkNode child = new DocumentChunkNode();
        child.setKind("CHILD");
        AiProfileNode profile = new AiProfileNode();
        profile.setId("profile-1");

        new GraphExtractionStage(graphExtractionService).execute(
            document,
            List.of(parent, child),
            profile.facts(),
            false
        );

        verify(graphExtractionService).extract(document, List.of(parent), false);
    }
}
