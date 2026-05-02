package io.github.vfedoriv.graphrag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.vfedoriv.graphrag.domain.DocumentStatus;
import io.github.vfedoriv.graphrag.domain.DocumentUploadNode;
import io.github.vfedoriv.graphrag.repository.DocumentUploadRepository;
import io.github.vfedoriv.graphrag.storage.BinaryStorageService;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    @Mock
    private BinaryStorageService binaryStorageService;
    @Mock
    private DocumentUploadRepository documentUploadRepository;
    @InjectMocks
    private DocumentUploadService documentUploadService;

    @Test
    void computesSha256Deterministically() {
        String hash = documentUploadService.sha256("hello".getBytes());
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void skipsDuplicateByKnowledgeBaseAndHash() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "same".getBytes());
        String hash = documentUploadService.sha256(file.getBytes());

        DocumentUploadNode existing = new DocumentUploadNode();
        existing.setId("doc-existing");
        existing.setKnowledgeBaseId("kb-1");
        existing.setSha256(hash);

        when(documentUploadRepository.findByKnowledgeBaseIdAndSha256("kb-1", hash)).thenReturn(Optional.of(existing));

        DocumentUploadNode result = documentUploadService.upload("kb-1", file);
        assertThat(result.getId()).isEqualTo("doc-existing");
        verify(documentUploadRepository).findByKnowledgeBaseIdAndSha256("kb-1", hash);
    }

    @Test
    void tracksFailedStatusWhenStorageFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "test".getBytes());
        String hash = documentUploadService.sha256(file.getBytes());
        when(documentUploadRepository.findByKnowledgeBaseIdAndSha256("kb-1", hash)).thenReturn(Optional.empty());
        when(binaryStorageService.store(any(), any(), any(), any())).thenThrow(new IOException("disk full"));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DocumentUploadNode result = documentUploadService.upload("kb-1", file);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("disk full");
    }

    @Test
    void persistsUploadedMetadataAndContentUri() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contract.txt", "text/plain", "hello".getBytes());
        String hash = documentUploadService.sha256(file.getBytes());
        when(documentUploadRepository.findByKnowledgeBaseIdAndSha256("kb-1", hash)).thenReturn(Optional.empty());
        when(binaryStorageService.store(any(), any(), any(), any())).thenReturn(URI.create("file:///tmp/doc.bin"));
        when(documentUploadRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DocumentUploadNode result = documentUploadService.upload("kb-1", file);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(result.getContentUri()).isEqualTo("file:///tmp/doc.bin");
        assertThat(result.getOriginalFilename()).isEqualTo("contract.txt");
        assertThat(result.getSizeBytes()).isEqualTo(5);
        assertThat(result.getUploadedAt()).isNotNull();

        ArgumentCaptor<DocumentUploadNode> captor = ArgumentCaptor.forClass(DocumentUploadNode.class);
        verify(documentUploadRepository).save(captor.capture());
        assertThat(captor.getValue().getSha256()).isEqualTo(hash);
    }
}
