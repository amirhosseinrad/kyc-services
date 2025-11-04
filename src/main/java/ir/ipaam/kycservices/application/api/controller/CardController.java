package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.service.CardStatusService;
import ir.ipaam.kycservices.application.service.impl.CardValidationServiceImpl;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/card")
@Tag(name = "Card Document Upload", description = "Collect front and back images of the customer's national card.")
public class CardController {

    public static final long MAX_IMAGE_SIZE_BYTES = CardValidationServiceImpl.MAX_IMAGE_SIZE_BYTES;

    private final CardValidationServiceImpl cardValidationServiceImpl;
    private final CardStatusService cardStatusService;


    @Operation(
            summary = "Upload card images",
            description = "Receives the front and back scans of a customer's national card, compresses large images "
                    + "when possible, persists them, and updates the workflow state."
    )
    @PostMapping( consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        CardDocumentUploadResult result = cardValidationServiceImpl.uploadCardDocuments(frontImage, backImage, processInstanceId);
        return ResponseEntity.status(result.status()).body(result.body());
    }

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
