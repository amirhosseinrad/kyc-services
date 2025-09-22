package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ENGLISH_INFO_FAILED;

@Component
@RequiredArgsConstructor
public class ProvideEnglishPersonalInfoWorker {

    private static final Logger log = LoggerFactory.getLogger(ProvideEnglishPersonalInfoWorker.class);
    static final String STEP_NAME = "ENGLISH_PERSONAL_INFO_PROVIDED";

    private final KycUserTasks kycUserTasks;
    private final KycServiceTasks kycServiceTasks;

    @JobWorker(type = "provide-english-personal-info")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String processInstanceId = null;
        try {
            processInstanceId = extractString(variables.get("processInstanceId"), "processInstanceId");
            String firstNameEn = extractString(variables.get("firstNameEn"), "firstNameEn");
            String lastNameEn = extractString(variables.get("lastNameEn"), "lastNameEn");
            String email = extractString(variables.get("email"), "email");
            String telephone = extractString(variables.get("telephone"), "telephone");

            kycUserTasks.provideEnglishPersonalInfo(firstNameEn, lastNameEn, email, telephone, processInstanceId);
            return Map.of("englishPersonalInfoProvided", true);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logFailure(processInstanceId);
            log.error("Failed to provide english personal info for job {}: {}", job.getKey(), WORKFLOW_ENGLISH_INFO_FAILED, e);
            throw new WorkflowTaskException(WORKFLOW_ENGLISH_INFO_FAILED, e);
        }
    }

    private void logFailure(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            kycServiceTasks.logFailureAndRetry(STEP_NAME, WORKFLOW_ENGLISH_INFO_FAILED, processInstanceId);
        } catch (RuntimeException loggingError) {
            log.warn("Failed to log retry for process {} and step {}", processInstanceId, STEP_NAME, loggingError);
        }
    }

    private String extractString(Object value, String fieldName) {
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(fieldName + " must be provided");
            }
            return trimmed;
        }
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        throw new IllegalArgumentException(fieldName + " must be a non-empty string");
    }
}
