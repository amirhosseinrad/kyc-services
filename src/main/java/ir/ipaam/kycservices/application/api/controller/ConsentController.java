package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
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
import java.util.Map;
import java.util.Optional;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.CONSENT_MUST_BE_TRUE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.TERMS_VERSION_REQUIRED;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/consent")
@Validated
public class ConsentController {

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final ZeebeClient zeebeClient;
    private final ConsentRepository consentRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> acceptConsent(@Valid @RequestBody ConsentRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        String termsVersion = normalizeTermsVersion(request.termsVersion());
        if (!request.accepted()) {
            throw new IllegalArgumentException(CONSENT_MUST_BE_TRUE);
        }
        ProcessInstance processInstance = kycProcessInstanceRepository
                .findByCamundaInstanceId(processInstanceId).get();
        // Check if already accepted
        if (consentRepository.findByProcessAndAccepted(processInstance, true).isPresent()) {
            log.info("Consent already accepted for process {}", processInstanceId);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "processInstanceId", processInstanceId,
                    "termsVersion", termsVersion,
                    "status", "CONSENT_ALREADY_ACCEPTED"
            ));
        }


        if (kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId).isEmpty()) {
            log.warn("Process instance with id {} not found", processInstanceId);
            throw new ResourceNotFoundException(PROCESS_NOT_FOUND);
        }

        AcceptConsentCommand command = new AcceptConsentCommand(
                processInstanceId,
                termsVersion,
                true
        );

        commandGateway.sendAndWait(command);

        updateWorkflowVariables(processInstanceId, termsVersion);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "processInstanceId", processInstanceId,
                "termsVersion", termsVersion,
                "status", "CONSENT_ACCEPTED"
        ));
    }

    private void updateWorkflowVariables(String processInstanceId, String termsVersion) {
        long processKey = parseProcessInstanceKey(processInstanceId);
        zeebeClient.newPublishMessageCommand()
                .messageName("consent-accepted")
                .correlationKey(processInstanceId)
                .variables(Map.of(
                        "accepted", true,
                        "termsVersion", termsVersion,
                        "kycStatus", "CONSENT_ACCEPTED"
                ))
                .send()
                .join();
    }

    private long parseProcessInstanceKey(String processInstanceId) {
        try {
            return Long.parseLong(processInstanceId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("processInstanceId must be a numeric value", ex);
        }
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private String normalizeTermsVersion(String termsVersion) {
        if (!StringUtils.hasText(termsVersion)) {
            throw new IllegalArgumentException(TERMS_VERSION_REQUIRED);
        }
        return termsVersion.trim();
    }
}
