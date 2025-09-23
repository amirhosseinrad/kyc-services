package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import ir.ipaam.kycservices.application.api.dto.KycStatusRequest;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.application.api.dto.StartKycRequest;
import ir.ipaam.kycservices.application.api.dto.StartKycResponse;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Validated
public class KycProcessController {

    private final KycServiceTasks kycServiceTasks;
    private final CommandGateway commandGateway;
    private final ZeebeClient zeebeClient;

    @Operation(summary = "Start a new KYC process")
    @PostMapping("/process")
    public ResponseEntity<StartKycResponse> startProcess(@Valid @RequestBody StartKycRequest request) {
        ProcessInstance existingInstance = kycServiceTasks.checkKycStatus(request.nationalCode());
        if (existingInstance != null) {
            String existingCamundaId = existingInstance.getCamundaInstanceId();
            String existingStatus = existingInstance.getStatus();
            if (existingCamundaId != null && !existingCamundaId.isBlank() && existingStatus != null) {
                String normalizedStatus = existingStatus.trim();
                if (!normalizedStatus.isEmpty()
                        && !"UNKNOWN".equalsIgnoreCase(normalizedStatus)
                        && !"COMPLETED".equalsIgnoreCase(normalizedStatus)) {
                    return ResponseEntity.ok(new StartKycResponse(existingCamundaId, normalizedStatus));
                }
            }
        }

        long key = zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId("kyc-process")
                .latestVersion()
                .variables(Map.of("nationalCode", request.nationalCode()))
                .send()
                .join()
                .getProcessInstanceKey();
        String processInstanceKey = Long.toString(key);
        commandGateway.send(new StartKycProcessCommand(processInstanceKey, request.nationalCode()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new StartKycResponse(processInstanceKey, "STARTED"));
    }
    @Operation(summary = "Get KYC process state")
    @PostMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(@Valid @RequestBody KycStatusRequest request) {
        ProcessInstance instance = kycServiceTasks.checkKycStatus(request.nationalCode());
        return ResponseEntity.ok(KycStatusResponse.success(instance));
    }
}
