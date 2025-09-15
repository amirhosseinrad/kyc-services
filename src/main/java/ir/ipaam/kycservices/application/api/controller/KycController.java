package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import ir.ipaam.kycservices.application.api.dto.KycStatusRequest;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.application.api.dto.KycStatusUpdateRequest;
import ir.ipaam.kycservices.application.api.dto.StartKycRequest;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
public class KycController {

    private final KycServiceTasks kycServiceTasks;
    private final CommandGateway commandGateway;
    private final ZeebeClient zeebeClient;

    @Operation(summary = "Start a new KYC process")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Process started"),
            @ApiResponse(responseCode = "400", description = "Invalid input")
    })
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> startProcess(@Valid @RequestBody StartKycRequest request) {
        try {
            long key = zeebeClient.newCreateInstanceCommand()
                    .bpmnProcessId("bpmn/kyc-process")
                    .latestVersion()
                    .variables(Map.of("nationalCode", request.nationalCode()))
                    .send()
                    .join()
                    .getProcessInstanceKey();
            String processInstanceKey = Long.toString(key);
            commandGateway.send(new StartKycProcessCommand(processInstanceKey, request.nationalCode()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("processInstanceKey", processInstanceKey));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(@Valid @RequestBody KycStatusRequest request) {
        try {
            KycProcessInstance instance = kycServiceTasks.checkKycStatus(request.nationalCode());
            return ResponseEntity.ok(KycStatusResponse.success(instance));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(KycStatusResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(KycStatusResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/status/{processInstanceId}")
    public ResponseEntity<Void> updateStatus(
            @PathVariable("processInstanceId")
            @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "invalid processInstanceId") String processInstanceId,
            @Valid @RequestBody KycStatusUpdateRequest request) {
        try {
            kycServiceTasks.updateKycStatus(processInstanceId, request.status());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
