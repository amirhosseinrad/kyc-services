package ir.ipaam.kycservices.application.service.dto;

import org.springframework.http.HttpStatus;

import java.util.Map;

public record CardDocumentUploadResult(HttpStatus status, Map<String, Object> body) {

    public static CardDocumentUploadResult of(HttpStatus status, Map<String, Object> body) {
        return new CardDocumentUploadResult(status, body);
    }

    public static CardDocumentUploadResult error(HttpStatus status, String messageKey, Map<String, Object> details) {
        Map<String, Object> body = Map.of(
                "status", "CARD_DOCUMENTS_REJECTED",
                "message", messageKey,
                "details", details == null ? Map.of() : details
        );
        return new CardDocumentUploadResult(status, body);
    }
}
