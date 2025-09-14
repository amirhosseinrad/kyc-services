package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/kyc")
public class KycController {

    private final KycServiceTasks tasks;

    public KycController(KycServiceTasks tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> getStatus(@RequestParam(required = false) String nationalCode) {
        if (nationalCode == null || nationalCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "nationalCode is required"));
        }
        try {
            String status = tasks.checkKycStatus(nationalCode);
            return ResponseEntity.ok(Map.of("status", status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
