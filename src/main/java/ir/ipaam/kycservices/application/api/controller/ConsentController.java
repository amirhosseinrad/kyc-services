package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
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

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/consent")
@Validated
public class ConsentController {

    private final CommandGateway commandGateway;

    @PostMapping
    public ResponseEntity<Map<String, Object>> acceptConsent(@Valid @RequestBody ConsentRequest request) {
        try {
            String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
            String termsVersion = normalizeTermsVersion(request.termsVersion());
            if (!request.accepted()) {
                throw new IllegalArgumentException("accepted must be true");
            }

            AcceptConsentCommand command = new AcceptConsentCommand(
                    processInstanceId,
                    termsVersion,
                    true
            );

            commandGateway.sendAndWait(command);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "processInstanceId", processInstanceId,
                    "termsVersion", termsVersion,
                    "status", "CONSENT_ACCEPTED"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid consent request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (CommandExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                log.warn("Command execution rejected: {}", illegalArgumentException.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", illegalArgumentException.getMessage()));
            }
            log.error("Command execution failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept consent"));
        } catch (Exception e) {
            log.error("Unexpected error while accepting consent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept consent"));
        }
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }

    private String normalizeTermsVersion(String termsVersion) {
        if (!StringUtils.hasText(termsVersion)) {
            throw new IllegalArgumentException("termsVersion must be provided");
        }
        return termsVersion.trim();
    }
}
