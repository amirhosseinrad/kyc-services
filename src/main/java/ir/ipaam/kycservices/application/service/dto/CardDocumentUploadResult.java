package ir.ipaam.kycservices.application.service.dto;

import org.springframework.http.HttpStatus;

import java.util.Map;

public record CardDocumentUploadResult(HttpStatus status, Map<String, Object> body) {

    public static CardDocumentUploadResult of(HttpStatus status, Map<String, Object> body) {
        return new CardDocumentUploadResult(status, body);
    }
}
