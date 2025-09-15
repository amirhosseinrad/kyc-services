package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.dto.KycStatusRequest;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.application.api.dto.KycStatusUpdateRequest;
import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
@Validated
public class KycController {

    private final KycServiceTasks kycServiceTasks;

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

    @PutMapping("/status/{processInstanceId}")
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
