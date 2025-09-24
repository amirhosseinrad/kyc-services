package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
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
    static final String STEP_NAME = "CONSENT_ACCEPTED";

    private final KycUserTasks kycUserTasks;
    private final KycServiceTasks kycServiceTasks;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @JobWorker(type = "accept-consent")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String processInstanceId = null;
        try {
            processInstanceId = extractString(variables.get("processInstanceId"), "processInstanceId");
            ensureConsentPayloadPresent(variables, processInstanceId);
            String termsVersion = extractString(variables.get("termsVersion"), "termsVersion");
            boolean accepted = extractBoolean(variables.get("accepted"), "accepted");
            boolean card = resolveCardFlag(variables, processInstanceId);

            kycUserTasks.acceptConsent(termsVersion, accepted, processInstanceId);
            return Map.of(
                    "consentAccepted", accepted,
                    "card", card,
                    "kycStatus", STEP_NAME
            );
        } catch (MissingConsentVariablesException e) {
            log.info("Consent payload not yet available for job {} and process {}", job.getKey(), processInstanceId);
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logFailure(processInstanceId);
            log.error("Failed to accept consent for job {}: {}", job.getKey(), WORKFLOW_ACCEPT_CONSENT_FAILED, e);
            throw new WorkflowTaskException(WORKFLOW_ACCEPT_CONSENT_FAILED, e);
        }
    }

    private void logFailure(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            kycServiceTasks.logFailureAndRetry(STEP_NAME, WORKFLOW_ACCEPT_CONSENT_FAILED, processInstanceId);
        } catch (RuntimeException loggingError) {
            log.warn("Failed to log retry for process {} and step {}", processInstanceId, STEP_NAME, loggingError);
        }
    }

    private void ensureConsentPayloadPresent(Map<String, Object> variables, String processInstanceId) {
        Object termsVersion = variables.get("termsVersion");
        Object accepted = variables.get("accepted");
        if (termsVersion == null || accepted == null) {
            throw new MissingConsentVariablesException(processInstanceId);
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

    private boolean resolveCardFlag(Map<String, Object> variables, String processInstanceId) {
        Boolean card = extractOptionalBoolean(variables.get("card"));
        if (card != null) {
            return card;
        }

        return kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .map(ProcessInstance::getCustomer)
                .map(Customer::getHasNewNationalCard)
                .orElseThrow(() -> new MissingConsentVariablesException(processInstanceId));
    }

    private Boolean extractOptionalBoolean(Object value) {
        if (value == null) {
            return null;
        }
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
            throw new IllegalArgumentException("card must be 'true' or 'false'");
        }
        throw new IllegalArgumentException("card must be a boolean");
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

    static class MissingConsentVariablesException extends RuntimeException {

        MissingConsentVariablesException(String processInstanceId) {
            super("Consent variables missing for process " + processInstanceId);
        }
    }
}
