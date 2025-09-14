package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.dto.KycStatusRequest;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycServiceTasks kycServiceTasks;

    @PostMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(@Valid @RequestBody KycStatusRequest request) {
        try {
            String status = kycServiceTasks.checkKycStatus(request.nationalCode());
            return ResponseEntity.ok(KycStatusResponse.success(status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(KycStatusResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(KycStatusResponse.error(e.getMessage()));
        }
    }
}
