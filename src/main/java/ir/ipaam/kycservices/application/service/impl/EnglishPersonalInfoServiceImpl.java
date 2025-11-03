package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.EnglishPersonalInfoService;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.EMAIL_INVALID;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.EMAIL_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.ENGLISH_FIRST_NAME_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.ENGLISH_LAST_NAME_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.TELEPHONE_REQUIRED;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnglishPersonalInfoServiceImpl implements EnglishPersonalInfoService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\\s]+@[^@\\\s]+\\.[^@\\\s]+$");

    private static final String STEP_ENGLISH_PERSONAL_INFO_PROVIDED = "ENGLISH_PERSONAL_INFO_PROVIDED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;

    @Override
    public ResponseEntity<Map<String, Object>> provideEnglishPersonalInfo(EnglishPersonalInfoRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", processInstanceId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                processInstanceId,
                STEP_ENGLISH_PERSONAL_INFO_PROVIDED)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "processInstanceId", processInstanceId,
                    "status", "ENGLISH_PERSONAL_INFO_ALREADY_PROVIDED"
            ));
        }

        String firstNameEn = normalizeRequiredText(request.firstNameEn(), ENGLISH_FIRST_NAME_REQUIRED);
        String lastNameEn = normalizeRequiredText(request.lastNameEn(), ENGLISH_LAST_NAME_REQUIRED);
        String email = normalizeEmail(request.email());
        String telephone = normalizeRequiredText(request.telephone(), TELEPHONE_REQUIRED);

        ProvideEnglishPersonalInfoCommand command = new ProvideEnglishPersonalInfoCommand(
                processInstanceId,
                firstNameEn,
                lastNameEn,
                email,
                telephone
        );

        commandGateway.sendAndWait(command);

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(processInstanceId, hasNewCard);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "processInstanceId", processInstanceId,
                "firstNameEn", firstNameEn,
                "lastNameEn", lastNameEn,
                "email", email,
                "telephone", telephone,
                "status", "ENGLISH_PERSONAL_INFO_PROVIDED"
        ));
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "ENGLISH_PERSONAL_INFO_PROVIDED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("english-personal-info-provided")
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private String normalizeRequiredText(String value, String messageKey) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(messageKey);
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeRequiredText(email, EMAIL_REQUIRED);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(EMAIL_INVALID);
        }
        return normalized;
    }
}

