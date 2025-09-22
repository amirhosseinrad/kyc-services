package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.DocumentType;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DocumentRetrievalService {

    private final DocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;

    public RetrievedDocument retrieveLatestDocument(String nationalCode, DocumentType documentType) {
        String repositoryDocumentType = documentType.name();
        Document document = documentRepository
                .findTopByTypeAndProcess_Customer_NationalCodeAndVerifiedTrueOrderByIdDesc(repositoryDocumentType, nationalCode)
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

        DocumentType retrievedType;
        try {
            retrievedType = DocumentType.fromValue(document.getType());
        } catch (IllegalArgumentException ex) {
            throw new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND, ex);
        }

        return new RetrievedDocument(retrievedType, content);
    }

    public record RetrievedDocument(DocumentType documentType, byte[] content) {

        public RetrievedDocument {
            if (documentType == null) {
                throw new IllegalArgumentException("documentType must not be null");
            }
            if (content == null) {
                throw new IllegalArgumentException("content must not be null");
            }
        }
    }
}
