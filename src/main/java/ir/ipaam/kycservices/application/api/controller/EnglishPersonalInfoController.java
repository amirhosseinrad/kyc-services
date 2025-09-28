package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.EMAIL_INVALID;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.EMAIL_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ENGLISH_FIRST_NAME_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ENGLISH_LAST_NAME_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.TELEPHONE_REQUIRED;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/english-info")
@Validated
@Tag(name = "English Personal Info", description = "Capture Latin-script customer information required for downstream compliance.")
public class EnglishPersonalInfoController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\\s]+@[^@\\\s]+\\.[^@\\\s]+$");

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final ZeebeClient zeebeClient;

    @Operation(
            summary = "Provide English personal details",
            description = "Validates and stores the applicant's English first name, last name, email, and telephone. "
                    + "Publishes a workflow update after the information is accepted."
    )
    @PostMapping
    public ResponseEntity<Map<String, Object>> provideEnglishPersonalInfo(@Valid @RequestBody EnglishPersonalInfoRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", processInstanceId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

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
