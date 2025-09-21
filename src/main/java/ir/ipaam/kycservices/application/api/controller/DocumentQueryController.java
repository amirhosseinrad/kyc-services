package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.infrastructure.service.DocumentNotFoundException;
import ir.ipaam.kycservices.infrastructure.service.DocumentRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
public class DocumentQueryController {

    private final DocumentRetrievalService documentRetrievalService;

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> fetchLatestDocument(
            @RequestParam("nationalCode") String nationalCode,
            @RequestParam("documentType") String documentType) {
        DocumentRetrievalService.RetrievedDocument document;
        try {
            document = documentRetrievalService.retrieveLatestDocument(nationalCode, documentType);
        } catch (DocumentNotFoundException ex) {
            throw new ResourceNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(document.content());
    }
}
