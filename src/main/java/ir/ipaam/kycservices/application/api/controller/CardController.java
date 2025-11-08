package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.dto.RecordTrackingNumberRequest;
import ir.ipaam.kycservices.application.service.CardService;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadRequest;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResponse;
import ir.ipaam.kycservices.application.service.dto.CardStatusResponse;
import ir.ipaam.kycservices.application.service.dto.CardTrackingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/card")
@Tag(name = "Card Services", description = "Collect card documents, record card status, and persist tracking numbers.")
public class CardController {

    public static final long MAX_IMAGE_SIZE_BYTES = CardService.MAX_IMAGE_SIZE_BYTES;

    private final CardService cardService;

    @Operation(
            summary = "Upload card images",
            description = "Receives the front and back scans of a customer's national card, compresses large images "
                    + "when possible, persists them, and updates the workflow state."
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CardDocumentUploadResponse> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        CardDocumentUploadRequest request = new CardDocumentUploadRequest(frontImage, backImage, processInstanceId);
        CardDocumentUploadResponse response = cardService.uploadCardDocuments(request);
        return ResponseEntity.status(resolveCardDocumentsStatus(response)).body(response);
    }

    @Operation(
            summary = "Record national card status",
            description = "Registers whether the applicant holds a new national card, persists the flag, and "
                    + "broadcasts a card-status-recorded workflow message."
    )
    @PostMapping("/status")
    public ResponseEntity<CardStatusResponse> updateCardStatus(@Valid @RequestBody CardStatusRequest request) {
        CardStatusResponse response = cardService.updateCardStatus(request);
        return ResponseEntity.status(resolveCardStatus(response)).body(response);
    }

    @Operation(
            summary = "Record national card tracking number",
            description = "Persists the national card tracking number and notifies the workflow about the update."
    )
    @PostMapping("/tracking")
    public ResponseEntity<CardTrackingResponse> recordTrackingNumber(
            @RequestBody RecordTrackingNumberRequest request) {
        CardTrackingResponse response = cardService.recordTrackingNumber(request);
        return ResponseEntity.ok(response);
    }

    private HttpStatus resolveCardDocumentsStatus(CardDocumentUploadResponse response) {
        if ("CARD_DOCUMENTS_ALREADY_UPLOADED".equals(response.status())) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.ACCEPTED;
    }

    private HttpStatus resolveCardStatus(CardStatusResponse response) {
        if ("CARD_STATUS_ALREADY_RECORDED".equals(response.status())) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.ACCEPTED;
    }
}
