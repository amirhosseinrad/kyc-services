package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ACCEPT_CONSENT_FAILED;

@Component
@RequiredArgsConstructor
public class AcceptConsentWorker {

    private static final Logger log = LoggerFactory.getLogger(AcceptConsentWorker.class);

    private final KycUserTasks kycUserTasks;

    @JobWorker(type = "accept-consent")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        try {
            String processInstanceId = extractString(variables.get("processInstanceId"), "processInstanceId");
            String termsVersion = extractString(variables.get("termsVersion"), "termsVersion");
            boolean accepted = extractBoolean(variables.get("accepted"), "accepted");

            kycUserTasks.acceptConsent(termsVersion, accepted, processInstanceId);
            return Map.of("consentAccepted", accepted);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Failed to accept consent for job {}: {}", job.getKey(), WORKFLOW_ACCEPT_CONSENT_FAILED, e);
            throw new WorkflowTaskException(WORKFLOW_ACCEPT_CONSENT_FAILED, e);
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

    private boolean extractBoolean(Object value, String fieldName) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String trimmed = stringValue.trim();
            if (trimmed.equalsIgnoreCase("true")) {
                return true;
            }
            if (trimmed.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException(fieldName + " must be 'true' or 'false'");
        }
        throw new IllegalArgumentException(fieldName + " must be a boolean");
    }
}
