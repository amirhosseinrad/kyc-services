package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRetrievalServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MinioStorageService minioStorageService;

    private DocumentRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new DocumentRetrievalService(documentRepository, minioStorageService);
    }

    @Test
    void retrieveLatestDocumentReturnsBytesFromStorage() {
        Document document = new Document();
        document.setType("PHOTO");
        document.setStoragePath("bucket/object");

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));
        when(minioStorageService.download("bucket/object")).thenReturn(new byte[]{1, 2, 3});

        DocumentRetrievalService.RetrievedDocument result =
                service.retrieveLatestDocument("0012345678", "PHOTO");

        assertThat(result.documentType()).isEqualTo("PHOTO");
        assertThat(result.content()).containsExactly(1, 2, 3);

        ArgumentCaptor<String> storagePathCaptor = ArgumentCaptor.forClass(String.class);
        verify(minioStorageService).download(storagePathCaptor.capture());
        assertThat(storagePathCaptor.getValue()).isEqualTo("bucket/object");
    }

    @Test
    void retrieveLatestDocumentThrowsWhenMetadataMissing() {
        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retrieveLatestDocument("0012345678", "PHOTO"))
                .isInstanceOf(DocumentNotFoundException.class);

        verifyNoInteractions(minioStorageService);
    }

    @Test
    void retrieveLatestDocumentThrowsWhenObjectMissing() {
        Document document = new Document();
        document.setType("PHOTO");
        document.setStoragePath("bucket/object");

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));
        when(minioStorageService.download("bucket/object"))
                .thenThrow(new NoSuchElementException("missing"));

        assertThatThrownBy(() -> service.retrieveLatestDocument("0012345678", "PHOTO"))
                .isInstanceOf(DocumentNotFoundException.class);
    }
}
