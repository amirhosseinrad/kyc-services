package ir.ipaam.kycservices.application.service.dto;

import org.springframework.http.HttpStatus;

import java.util.Map;

public record SignatureUploadResult(HttpStatus status, Map<String, Object> body) {

    public static SignatureUploadResult of(HttpStatus status, Map<String, Object> body) {
        return new SignatureUploadResult(status, body);
    }
}
