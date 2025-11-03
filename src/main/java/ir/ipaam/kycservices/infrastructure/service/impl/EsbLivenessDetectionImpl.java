package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.application.api.error.ErrorMessageKeys;
import ir.ipaam.kycservices.application.service.EsbLivenessDetection;
import ir.ipaam.kycservices.application.service.dto.LivenessCheckData;
import ir.ipaam.kycservices.infrastructure.service.dto.LivenessResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Slf4j
@Service
public class EsbLivenessDetectionImpl implements EsbLivenessDetection {

    private static final String VIDEO_PART_NAME = "video";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient faceDetectionWebClient;

    public EsbLivenessDetectionImpl(@Qualifier("faceDetectionWebClient") WebClient faceDetectionWebClient) {
        this.faceDetectionWebClient = faceDetectionWebClient;
    }

    @Override
    public LivenessCheckData check(byte[] content, String filename, MediaType contentType, String referenceId) {
        try {
            LivenessResponse response = faceDetectionWebClient.post()
                    .uri("/api/kyc/v0.1/faces/liveness")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(createMultipart(content, filename, contentType)))
                    .retrieve()
                    .bodyToMono(LivenessResponse.class)
                    .block(DEFAULT_TIMEOUT);

            if (response == null) {
                log.warn("Liveness response was null for reference {}", referenceId);
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED);
            }

            if (response.status() == null || response.status().code() == null || response.status().code() != 200) {
                log.warn("Liveness service returned non-success status: {}", response.status());
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED);
            }

            LivenessResponse.Result result = response.result();
            if (result == null || result.data() == null || result.data().video() == null) {
                log.warn("Liveness response missing video data for reference {}", referenceId);
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED);
            }

            LivenessResponse.LivenessData data = result.data();
            LivenessResponse.Video video = data.video();
            return new LivenessCheckData(
                    data.trackId(),
                    video.framesCount(),
                    video.liveness(),
                    video.isReal()
            );
        } catch (WebClientResponseException ex) {
            log.warn("Liveness request rejected with status {}", ex.getStatusCode(), ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED, ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to verify liveness for reference {}", referenceId, ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED, ex);
        }
    }

    private MultiValueMap<String, HttpEntity<?>> createMultipart(byte[] content, String filename, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(VIDEO_PART_NAME, new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        if (StringUtils.hasText(filename)) {
                            return filename;
                        }
                        return VIDEO_PART_NAME + ".mp4";
                    }
                })
                .filename(StringUtils.hasText(filename) ? filename : VIDEO_PART_NAME + ".mp4")
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM);
        return builder.build();
    }
}
