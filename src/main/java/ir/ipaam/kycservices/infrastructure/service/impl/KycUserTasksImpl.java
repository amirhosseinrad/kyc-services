package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import ir.ipaam.kycservices.infrastructure.service.dto.UploadCardDocumentsResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KycUserTasksImpl implements KycUserTasks {

    private static final Logger log = LoggerFactory.getLogger(KycUserTasksImpl.class);
    static final String DOCUMENT_TYPE_FRONT = "CARD_FRONT";
    static final String DOCUMENT_TYPE_BACK = "CARD_BACK";

    @Qualifier("cardDocumentWebClient")
    private final WebClient documentWebClient;
    private final DocumentRepository documentRepository;
    private final KycProcessInstanceRepository processInstanceRepository;

    @Override
    public void uploadCardDocuments(byte[] frontImage, byte[] backImage, String processInstanceId) {
        validateInput(frontImage, backImage, processInstanceId);

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("frontImage", asResource(frontImage, "front-image"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("backImage", asResource(backImage, "back-image"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("processInstanceId", processInstanceId);

        UploadCardDocumentsResponse response = documentWebClient.post()
                .uri("/documents/card")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(UploadCardDocumentsResponse.class)
                .onErrorResume(throwable -> {
                    log.error("Failed to upload card documents for process {}", processInstanceId, throwable);
                    return Mono.error(throwable);
                })
                .block();

        if (response == null) {
            log.warn("Document storage service returned empty response for process {}", processInstanceId);
            return;
        }

        Optional<ProcessInstance> processInstanceOptional = processInstanceRepository.findByCamundaInstanceId(processInstanceId);
        ProcessInstance processInstance = processInstanceOptional.orElse(null);
        if (processInstance == null) {
            log.warn("No persisted process instance found for Camunda id {}", processInstanceId);
        }

        persistMetadata(response.getFront(), DOCUMENT_TYPE_FRONT, processInstanceId, processInstance);
        persistMetadata(response.getBack(), DOCUMENT_TYPE_BACK, processInstanceId, processInstance);
    }

    private void persistMetadata(DocumentMetadata metadata, String type, String processInstanceId,
                                 ProcessInstance processInstance) {
        if (metadata == null) {
            log.warn("Storage metadata for {} document is missing for process {}", type, processInstanceId);
            return;
        }

        Document document = new Document();
        document.setType(type);
        document.setStoragePath(metadata.getPath());
        document.setHash(metadata.getHash());
        document.setVerified(false);
        document.setProcess(processInstance);
        documentRepository.save(document);
        log.info("Persisted document metadata for type {} at path {} for process {}", type, metadata.getPath(), processInstanceId);
    }

    private void validateInput(byte[] frontImage, byte[] backImage, String processInstanceId) {
        if (frontImage == null || frontImage.length == 0) {
            throw new IllegalArgumentException("frontImage must be provided");
        }
        if (backImage == null || backImage.length == 0) {
            throw new IllegalArgumentException("backImage must be provided");
        }
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
    }

    private ByteArrayResource asResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    @Override
    public void uploadSignature(byte[] signatureImage, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void acceptConsent(String termsVersion, boolean accepted, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void provideAddress(String address, String zipCode, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadSelfieAndVideo(byte[] photo, byte[] video, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadIdPages(java.util.List<byte[]> pages, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void uploadCardBranchSelfieAndVideo(byte[] photo, byte[] video, String processInstanceId) {
        // TODO: implement integration
    }

    @Override
    public void provideEnglishPersonalInfo(String firstNameEn, String lastNameEn, String email, String telephone, String processInstanceId) {
        // TODO: implement integration
    }
}
