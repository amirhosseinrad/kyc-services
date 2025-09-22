package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

        document.setVerified(true);
        document.setEncrypted(false);
        document.setEncryptionIv(null);

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));
        when(minioStorageService.download("bucket/object", false, null)).thenReturn(new byte[]{1, 2, 3});

        DocumentRetrievalService.RetrievedDocument result =
                service.retrieveLatestDocument("0012345678", "PHOTO");

        assertThat(result.documentType()).isEqualTo("PHOTO");
        assertThat(result.content()).containsExactly(1, 2, 3);

        verify(minioStorageService).download("bucket/object", false, null);
    }

    @Test
    void retrieveLatestDocumentThrowsWhenMetadataMissing() {
        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678"))
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

        document.setVerified(true);
        document.setEncrypted(false);
        document.setEncryptionIv(null);

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));
        when(minioStorageService.download("bucket/object", false, null))
                .thenThrow(new NoSuchElementException("missing"));

        assertThatThrownBy(() -> service.retrieveLatestDocument("0012345678", "PHOTO"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void retrieveLatestDocumentRejectsUnverifiedDocument() {
        Document document = new Document();
        document.setType("PHOTO");
        document.setStoragePath("bucket/object");
        document.setVerified(false);
        document.setEncrypted(false);
        document.setEncryptionIv(null);

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.retrieveLatestDocument("0012345678", "PHOTO"))
                .isInstanceOf(DocumentNotFoundException.class);

        verifyNoInteractions(minioStorageService);
    }

    @Test
    void retrieveLatestDocumentDecryptsEncryptedContent() {
        Document document = new Document();
        document.setType("PHOTO");
        document.setStoragePath("bucket/object");
        document.setVerified(true);
        document.setEncrypted(true);
        document.setEncryptionIv("YWJj");

        when(documentRepository.findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc("PHOTO", "0012345678"))
                .thenReturn(Optional.of(document));
        when(minioStorageService.download("bucket/object", true, "YWJj")).thenReturn(new byte[]{4, 5, 6});

        DocumentRetrievalService.RetrievedDocument result =
                service.retrieveLatestDocument("0012345678", "PHOTO");

        assertThat(result.content()).containsExactly(4, 5, 6);
        verify(minioStorageService).download("bucket/object", true, "YWJj");
    }
}
