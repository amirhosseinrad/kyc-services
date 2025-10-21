package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.service.CardDocumentService;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
@Tag(name = "Card Document Upload", description = "Collect front and back images of the customer's national card.")
public class CardDocumentController {

    public static final long MAX_IMAGE_SIZE_BYTES = CardDocumentService.MAX_IMAGE_SIZE_BYTES;

    private final CardDocumentService cardDocumentService;

    @Operation(
            summary = "Upload card images",
            description = "Receives the front and back scans of a customer's national card, compresses large images "
                    + "when possible, persists them, and updates the workflow state."
    )
    @PostMapping(path = "/card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        CardDocumentUploadResult result = cardDocumentService.uploadCardDocuments(frontImage, backImage, processInstanceId);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
