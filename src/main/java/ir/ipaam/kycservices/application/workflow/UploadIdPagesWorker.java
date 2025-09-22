package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ID_UPLOAD_FAILED;

@Component
@RequiredArgsConstructor
public class UploadIdPagesWorker {

    static final long MAX_PAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB per page
    static final String STEP_NAME = "ID_PAGES_UPLOADED";

    private static final Logger log = LoggerFactory.getLogger(UploadIdPagesWorker.class);

    private final KycUserTasks kycUserTasks;
    private final KycServiceTasks kycServiceTasks;

    @JobWorker(type = "upload-id-pages")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String processInstanceId = null;
        try {
            List<byte[]> pages = extractPages(variables.get("pages"));
            processInstanceId = (String) variables.get("processInstanceId");
            if (processInstanceId == null || processInstanceId.isBlank()) {
                throw new IllegalArgumentException("processInstanceId must be provided");
            }

            for (int i = 0; i < pages.size(); i++) {
                validateSize(pages.get(i), "pages[" + i + "]");
            }

            kycUserTasks.uploadIdPages(pages, processInstanceId);
            return Map.of("idPagesUploaded", true);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logFailure(processInstanceId);
            log.error("Failed to upload ID pages for job {}: {}", job.getKey(), WORKFLOW_ID_UPLOAD_FAILED, e);
            throw new WorkflowTaskException(WORKFLOW_ID_UPLOAD_FAILED, e);
        }
    }

    private void logFailure(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            kycServiceTasks.logFailureAndRetry(STEP_NAME, WORKFLOW_ID_UPLOAD_FAILED, processInstanceId);
        } catch (RuntimeException loggingError) {
            log.warn("Failed to log retry for process {} and step {}", processInstanceId, STEP_NAME, loggingError);
        }
    }

    private List<byte[]> extractPages(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("pages are required");
        }
        List<byte[]> pages = new ArrayList<>();
        if (value instanceof List<?> list) {
            int index = 0;
            for (Object element : list) {
                pages.add(extractBinary(element, "pages[" + index + "]"));
                index++;
            }
        } else if (value instanceof byte[] bytes) {
            pages.add(bytes);
        } else if (value instanceof String stringValue) {
            pages.add(decodeBase64(stringValue, "pages[0]"));
        } else {
            throw new IllegalArgumentException("pages has unsupported type " + value.getClass());
        }

        if (pages.isEmpty()) {
            throw new IllegalArgumentException("pages must not be empty");
        }
        if (pages.size() > 4) {
            throw new IllegalArgumentException("No more than four pages may be provided");
        }

        return pages;
    }

    private byte[] extractBinary(Object value, String fieldName) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof String stringValue) {
            return decodeBase64(stringValue, fieldName);
        }
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        throw new IllegalArgumentException(fieldName + " has unsupported type " + value.getClass());
    }

    private byte[] decodeBase64(String value, String fieldName) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        try {
            return Base64.getDecoder().decode(trimmed.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            log.debug("Failed to decode base64 value for {}", fieldName, ex);
            throw new IllegalArgumentException(fieldName + " is not valid base64");
        }
    }

    private void validateSize(byte[] data, String fieldName) {
        if (data.length == 0) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        if (data.length > MAX_PAGE_SIZE_BYTES) {
            throw new IllegalArgumentException(fieldName + " exceeds max size of " + MAX_PAGE_SIZE_BYTES + " bytes");
        }
    }
}
