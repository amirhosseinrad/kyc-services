package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.dto.DocumentQueryRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.infrastructure.service.DocumentNotFoundException;
import ir.ipaam.kycservices.infrastructure.service.DocumentRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
public class DocumentQueryController {

    private final DocumentRetrievalService documentRetrievalService;

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
