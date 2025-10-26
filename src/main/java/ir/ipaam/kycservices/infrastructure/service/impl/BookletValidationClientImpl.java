package ir.ipaam.kycservices.infrastructure.service.impl;

import ir.ipaam.kycservices.application.service.BookletValidationClient;
import ir.ipaam.kycservices.application.service.dto.BookletValidationData;
import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.infrastructure.service.dto.BookletValidationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
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
@RequiredArgsConstructor
public class BookletValidationClientImpl implements BookletValidationClient {

    private static final String BOOKLET_PART_NAME = "image";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    @Qualifier("bookletValidationWebClient")
    private final WebClient bookletWebClient;

    @Override
    public BookletValidationData validate(byte[] content, String filename, MediaType contentType) {
        try {
            BookletValidationResponse response = bookletWebClient.post()
                    .uri("/api/kyc/v0.1/booklets/validate")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(createMultipart(content, filename, contentType)))
                    .retrieve()
                    .bodyToMono(BookletValidationResponse.class)
                    .block(DEFAULT_TIMEOUT);

            if (response == null) {
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED);
            }

            if (response.status() == null || response.status().code() == null || response.status().code() != 200) {
                log.warn("Booklet validation service returned non-success status: {}", response.status());
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED);
            }

            BookletValidationResponse.Result result = response.result();
            if (result == null || result.data() == null) {
                log.warn("Booklet validation response missing data");
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED);
            }

            BookletValidationResponse.ServiceStatus resultStatus = result.status();
            if (resultStatus != null && resultStatus.code() != null && resultStatus.code() != 200) {
                log.warn("Booklet validation result rejected with status code {}", resultStatus.code());
                throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED);
            }

            return result.data();
        } catch (WebClientResponseException ex) {
            log.warn("Booklet validation request rejected with status {}", ex.getStatusCode(), ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED, ex);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Failed to validate booklet page", ex);
            throw new IllegalArgumentException(ErrorMessageKeys.WORKFLOW_BOOKLET_VALIDATION_FAILED, ex);
        }
    }

    private MultiValueMap<String, Object> createMultipart(byte[] content, String filename, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part(BOOKLET_PART_NAME, new ByteArrayResource(content) {
                    @Override
                    public String getFilename() {
                        if (StringUtils.hasText(filename)) {
                            return filename;
                        }
                        return BOOKLET_PART_NAME + ".jpg";
                    }
                })
                .filename(StringUtils.hasText(filename) ? filename : BOOKLET_PART_NAME + ".jpg")
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM);
        return builder.build();
    }
}
