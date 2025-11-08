package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.dto.DocumentQueryRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.api.error.ErrorMessageKeys;
import ir.ipaam.kycservices.application.api.error.DocumentNotFoundException;
import ir.ipaam.kycservices.infrastructure.service.DocumentRetrievalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
@Tag(name = "Document Service", description = "Download previously uploaded KYC artifacts.")
public class DocumentQueryController {

    private final DocumentRetrievalService documentRetrievalService;

    @Operation(
            summary = "Download the latest document",
            description = "Streams the most recent stored document for the provided national code and document type. "
                    + "Returns HTTP 404 when the requested artifact is not available."
    )
    @PostMapping(value = "/latest", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> fetchLatestDocument(@Valid @RequestBody DocumentQueryRequest request) {
        DocumentRetrievalService.RetrievedDocument document;
        try {
            document = documentRetrievalService.retrieveLatestDocument(request.nationalCode(), request.documentType());
        } catch (DocumentNotFoundException ex) {
            throw new ResourceNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(document.content());
    }
}
