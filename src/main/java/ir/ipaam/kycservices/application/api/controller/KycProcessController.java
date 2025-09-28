package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.KycStatusRequest;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.application.api.dto.StartKycRequest;
import ir.ipaam.kycservices.application.api.dto.StartKycResponse;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Validated
@Tag(name = "KYC Process", description = "Start new KYC workflows and query their current status.")
public class KycProcessController {

    private final KycServiceTasks kycServiceTasks;
    private final CommandGateway commandGateway;
    private final ZeebeClient zeebeClient;

    @Operation(
            summary = "▶ Start a new KYC process",
            description = "Launches a Camunda workflow for the given national code unless an active instance already "
                    + "exists, returning the process instance identifier and initial status."
    )
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
    @Operation(
            summary = "❚❚ Get KYC process state",
            description = "Looks up the latest workflow snapshot for the supplied national code and maps it to the "
                    + "public KYC status response."
    )
    @PostMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(@Valid @RequestBody KycStatusRequest request) {
        ProcessInstance instance = kycServiceTasks.checkKycStatus(request.nationalCode());
        return ResponseEntity.ok(KycStatusResponse.success(instance));
    }
}
