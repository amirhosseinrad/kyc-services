package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import jakarta.validation.Valid;
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

import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/card")
@Validated
@Tag(name = "Card Status", description = "Track whether the applicant possesses a newer national card.")
public class CardStatusController {

    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final ZeebeClient zeebeClient;
    private final CommandGateway commandGateway;

    @Operation(
            summary = "Record national card status",
            description = "Registers whether the applicant holds a new national card, persists the flag, and "
                    + "broadcasts a card-status-recorded workflow message."
    )
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> updateCardStatus(@Valid @RequestBody CardStatusRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        boolean hasNewNationalCard = Boolean.TRUE.equals(request.hasNewNationalCard());

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException(PROCESS_NOT_FOUND));

        Customer customer = processInstance.getCustomer();
        if (customer == null) {
            log.warn("Process {} does not have an associated customer when recording card status", processInstanceId);
            throw new ResourceNotFoundException(PROCESS_NOT_FOUND);
        }

        parseProcessInstanceKey(processInstanceId);

        customer.setHasNewNationalCard(hasNewNationalCard);
        customerRepository.save(customer);

        commandGateway.sendAndWait(new UpdateKycStatusCommand(
                processInstanceId,
                "CARD_STATUS_RECORDED",
                "CARD_STATUS_RECORDED",
                "PASSED"
        ));
        PublishMessageCommandStep1 publishMessageCommand = zeebeClient.newPublishMessageCommand();
        PublishMessageCommandStep2 messageNamed = publishMessageCommand.messageName("card-status-recorded");
        PublishMessageCommandStep3 correlated = messageNamed.correlationKey(processInstanceId);
        correlated
                .variables(Map.of(
                        "card", hasNewNationalCard,
                        "processInstanceId", processInstanceId,
                        "kycStatus", "CARD_STATUS_RECORDED"
                ))
                .send()
                .join();

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "processInstanceId", processInstanceId,
                "hasNewNationalCard", hasNewNationalCard,
                "status", "CARD_STATUS_RECORDED"
        ));
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private long parseProcessInstanceKey(String processInstanceId) {
        try {
            return Long.parseLong(processInstanceId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("processInstanceId must be a numeric value", ex);
        }
    }
}
