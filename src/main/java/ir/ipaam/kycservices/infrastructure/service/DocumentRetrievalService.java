package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DocumentRetrievalService {

    private final DocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;

    public RetrievedDocument retrieveLatestDocument(String nationalCode, String documentType) {
        Document document = documentRepository
                .findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc(documentType, nationalCode)
                .orElseThrow(() -> new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND));

        if (!document.isVerified()) {
            throw new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND);
        }

        String storagePath = document.getStoragePath();
        if (storagePath == null || storagePath.isBlank()) {
            throw new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND);
        }

        byte[] content;
        try {
            content = minioStorageService.download(storagePath, document.isEncrypted(), document.getEncryptionIv());
        } catch (NoSuchElementException | IllegalArgumentException ex) {
            throw new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND, ex);
        }

        return new RetrievedDocument(document.getType(), content);
    }

    public record RetrievedDocument(String documentType, byte[] content) {

        public RetrievedDocument {
            if (content == null) {
                throw new IllegalArgumentException("content must not be null");
            }
        }
    }
}
