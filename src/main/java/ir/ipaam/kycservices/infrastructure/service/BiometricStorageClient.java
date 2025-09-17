package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class BiometricStorageClient {

    private static final Logger log = LoggerFactory.getLogger(BiometricStorageClient.class);
    private static final String UPLOAD_URI = "/documents/biometric";

    private final WebClient storageWebClient;

    public BiometricStorageClient(@Qualifier("cardDocumentWebClient") WebClient storageWebClient) {
        this.storageWebClient = storageWebClient;
    }

    public DocumentMetadata upload(DocumentPayloadDescriptor descriptor, String documentType, String processInstanceId) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", asResource(descriptor))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("documentType", documentType);
        bodyBuilder.part("processInstanceId", processInstanceId);

        DocumentMetadata metadata = storageWebClient.post()
                .uri(UPLOAD_URI)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(DocumentMetadata.class)
                .onErrorResume(throwable -> {
                    log.error("Failed to upload {} document for process {} to object storage", documentType, processInstanceId, throwable);
                    return Mono.error(throwable);
                })
                .block();

        if (metadata == null) {
            log.warn("Object storage returned empty metadata for {} document of process {}", documentType, processInstanceId);
        }

        return metadata;
    }

    private ByteArrayResource asResource(DocumentPayloadDescriptor descriptor) {
        return new ByteArrayResource(descriptor.data()) {
            @Override
            public String getFilename() {
                return descriptor.filename();
            }
        };
    }
}
