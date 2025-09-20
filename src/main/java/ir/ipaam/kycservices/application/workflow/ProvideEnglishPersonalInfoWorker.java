package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProvideEnglishPersonalInfoWorker {

    private static final Logger log = LoggerFactory.getLogger(ProvideEnglishPersonalInfoWorker.class);

    private final KycUserTasks kycUserTasks;

    @JobWorker(type = "provide-english-personal-info")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        try {
            String processInstanceId = extractString(variables.get("processInstanceId"), "processInstanceId");
            String firstNameEn = extractString(variables.get("firstNameEn"), "firstNameEn");
            String lastNameEn = extractString(variables.get("lastNameEn"), "lastNameEn");
            String email = extractString(variables.get("email"), "email");
            String telephone = extractString(variables.get("telephone"), "telephone");

            kycUserTasks.provideEnglishPersonalInfo(firstNameEn, lastNameEn, email, telephone, processInstanceId);
            return Map.of("englishPersonalInfoProvided", true);
        } catch (IllegalArgumentException e) {
            log.error("Invalid job payload for job {}: {}", job.getKey(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to provide english personal info for job {}", job.getKey(), e);
            throw new RuntimeException("Failed to provide english personal info", e);
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
