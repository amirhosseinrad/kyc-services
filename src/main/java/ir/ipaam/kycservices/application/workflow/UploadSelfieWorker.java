package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UploadSelfieWorker {

    static final long MAX_SELFIE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final Logger log = LoggerFactory.getLogger(UploadSelfieWorker.class);

    private final KycUserTasks kycUserTasks;

    @JobWorker(type = "upload-selfie")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        try {
            byte[] selfie = extractBinary(variables.get("selfieImage"), "selfieImage");
            String processInstanceId = extractProcessInstanceId(variables.get("processInstanceId"));

            kycUserTasks.uploadSelfie(selfie, processInstanceId);
            return Map.of("selfieUploaded", true);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload selfie for job {}", job.getKey(), e);
            throw new RuntimeException("Failed to upload selfie", e);
        }
    }

    private String extractProcessInstanceId(Object value) {
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("processInstanceId must not be empty");
            }
            return trimmed;
        }
        if (value == null) {
            throw new IllegalArgumentException("processInstanceId is required");
            }
        throw new IllegalArgumentException("processInstanceId has unsupported type " + value.getClass());
    }

    private byte[] extractBinary(Object value, String fieldName) {
        if (value instanceof byte[] bytes) {
            if (bytes.length == 0) {
                throw new IllegalArgumentException(fieldName + " must not be empty");
            }
            return bytes;
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must not be empty");
            }
            try {
                byte[] decoded = Base64.getDecoder().decode(trimmed.getBytes(StandardCharsets.UTF_8));
                if (decoded.length == 0) {
                    throw new IllegalArgumentException(fieldName + " must not be empty");
                }
                return decoded;
            } catch (IllegalArgumentException ex) {
                log.debug("Failed to decode base64 value for {}", fieldName, ex);
                throw new IllegalArgumentException(fieldName + " is not valid base64");
            }
        }
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        throw new IllegalArgumentException(fieldName + " has unsupported type " + value.getClass());
    }
}
