package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.application.service.ConsentService;
import ir.ipaam.kycservices.application.service.dto.ConsentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/consent")
@Validated
@Tag(name = "Consent", description = "Record customer approval for terms and conditions.")
public class ConsentController {

    private final ConsentService consentService;

    @Operation(
            summary = "Accept terms and conditions",
            description = "Stores a customer's consent decision for a specific terms version. Prevents duplicate "
                    + "acceptance and emits a consent-accepted message to the workflow engine."
    )
    @PostMapping
    public ResponseEntity<ConsentResponse> acceptConsent(@Valid @RequestBody ConsentRequest request) {
        ConsentResponse response = consentService.acceptConsent(request);
        HttpStatus status = "CONSENT_ALREADY_ACCEPTED".equals(response.status())
                ? HttpStatus.CONFLICT
                : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }
}
