package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.application.service.EsbFaceDetection;
import ir.ipaam.kycservices.application.service.dto.FaceDetectionData;
import ir.ipaam.kycservices.application.api.error.ErrorMessageKeys;
import ir.ipaam.kycservices.infrastructure.service.dto.FaceDetectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Comparator;

@Slf4j
@Service
public class EsbFaceDetectionImpl implements EsbFaceDetection {

    private static final String SELFIE_PART_NAME = "image";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient faceDetectionWebClient;

    public EsbFaceDetectionImpl(@Qualifier("faceDetectionWebClient") WebClient faceDetectionWebClient) {
        this.faceDetectionWebClient = faceDetectionWebClient;
    }

    @Override
    public FaceDetectionData detect(byte[] content, String filename, MediaType contentType, String referenceId) {
        try {
            FaceDetectionResponse response = faceDetectionWebClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/api/kyc/v0.1/faces/detect")
                            .queryParam("referenceid", referenceId)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(createMultipart(content, filename, contentType)))
                    .retrieve()
                    .bodyToMono(FaceDetectionResponse.class)
                    .block(DEFAULT_TIMEOUT);

            if (response == null) {
                log.warn("Face detection response was null");
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED);
            }

            if (response.status() == null || response.status().code() == null || response.status().code() != 200) {
                log.warn("Face detection service returned non-success status: {}", response.status());
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED);
            }

            FaceDetectionResponse.Result result = response.result();
            if (result == null || result.data() == null) {
                log.warn("Face detection response missing data block");
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED);
            }

            FaceDetectionResponse.Data data = result.data();
            if (CollectionUtils.isEmpty(data.faces())) {
                log.warn("Face detection response did not contain any faces for reference {}", referenceId);
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED);
            }

            Double highestConfidence = data.faces().stream()
                    .filter(face -> face != null && face.confidence() != null)
                    .map(FaceDetectionResponse.Face::confidence)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            return new FaceDetectionData(data.trackId(), highestConfidence);
        } catch (WebClientResponseException ex) {
            log.warn("Face detection request rejected with status {}", ex.getStatusCode(), ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED, ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to validate selfie image", ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED, ex);
        }
    }

    private MultiValueMap<String, HttpEntity<?>> createMultipart(byte[] content, String filename, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(SELFIE_PART_NAME, new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        if (StringUtils.hasText(filename)) {
                            return filename;
                        }
                        return SELFIE_PART_NAME + ".jpg";
                    }
                })
                .filename(StringUtils.hasText(filename) ? filename : SELFIE_PART_NAME + ".jpg")
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM);
        return builder.build();
    }
}
