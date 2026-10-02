package io.github.vfedoriv.graphrag.documents.application.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.documents.application.management.DocumentUploadService;
import io.github.vfedoriv.graphrag.documents.application.processing.DocumentParsingService;
import io.github.vfedoriv.graphrag.documents.contracts.DocumentSourceInputs;
import io.github.vfedoriv.graphrag.documents.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.documents.ports.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.error.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentSourceInputsFacadeTest {
    private final DocumentUploadRepository repository = mock(DocumentUploadRepository.class);
    private final DocumentUploadService binaries = mock(DocumentUploadService.class);
    private final DocumentParsingService parser = mock(DocumentParsingService.class);
    private final DocumentSourceInputsFacade facade = new DocumentSourceInputsFacade(repository, binaries, parser);

    @Test
    void missingAndForeignDocumentsShareTheNotFoundError() {
        DocumentUploadNode foreign = new DocumentUploadNode();
        foreign.setId("document");
        foreign.setKnowledgeBaseId("other-kb");
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.findById("document")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> facade.readOwned("kb", "missing"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Document not found in knowledge base: missing");
        assertThatThrownBy(() -> facade.readOwned("kb", "document"))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Document not found in knowledge base: document");
        verifyNoInteractions(binaries, parser);
    }

    @Test
    void inspectOwnedReturnsMetadataWithoutReadingDocumentContent() {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("document");
        document.setKnowledgeBaseId("kb");
        document.setOriginalFilename("source.txt");
        document.setContentType("text/plain");
        document.setSizeBytes(42L);
        document.setSha256("source-hash");
        document.setContentUri("private-storage-uri");
        when(repository.findById("document")).thenReturn(Optional.of(document));

        assertThat(facade.inspectOwned("kb", "document"))
            .contains(new DocumentSourceInputs.Metadata(
                "document", "source.txt", "text/plain", 42L, "source-hash"));
        verifyNoInteractions(binaries, parser);
    }

    @Test
    void inspectOwnedReturnsEmptyForMissingAndForeignDocuments() {
        DocumentUploadNode foreign = new DocumentUploadNode();
        foreign.setId("foreign-document");
        foreign.setKnowledgeBaseId("other-kb");
        when(repository.findById("missing")).thenReturn(Optional.empty());
        when(repository.findById("foreign-document")).thenReturn(Optional.of(foreign));

        assertThat(facade.inspectOwned("kb", "missing")).isEmpty();
        assertThat(facade.inspectOwned("kb", "foreign-document")).isEmpty();
        verifyNoInteractions(binaries, parser);
    }

    @Test
    void readProvidesCopiedBytesWithoutParsing() throws Exception {
        DocumentUploadNode document = new DocumentUploadNode();
        document.setId("document");
        document.setKnowledgeBaseId("kb");
        document.setOriginalFilename("source.txt");
        document.setContentType("text/plain");
        document.setContentUri("stored-uri");
        byte[] stored = new byte[] {1, 2, 3};
        when(repository.findById("document")).thenReturn(Optional.of(document));
        when(binaries.readContent("stored-uri")).thenReturn(stored);

        DocumentSourceInputs.Source source = facade.readOwned("kb", "document");
        stored[0] = 9;
        source.bytes()[1] = 9;

        assertThat(source.byteCount()).isEqualTo(3);
        assertThat(source.bytes()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        verifyNoInteractions(parser);
    }
}
