package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.service.CardStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/card")
@Validated
@Tag(name = "Card Status", description = "Track whether the applicant possesses a newer national card.")
public class CardStatusController {

    private final CardStatusService cardStatusService;

    @Operation(
            summary = "Record national card status",
            description = "Registers whether the applicant holds a new national card, persists the flag, and "
                    + "broadcasts a card-status-recorded workflow message."
    )
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> updateCardStatus(@Valid @RequestBody CardStatusRequest request) {
        return cardStatusService.updateCardStatus(request);
    }
}
