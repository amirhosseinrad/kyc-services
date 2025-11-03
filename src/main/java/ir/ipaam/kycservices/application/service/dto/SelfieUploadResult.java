package ir.ipaam.kycservices.application.service.dto;

import org.springframework.http.HttpStatus;

import java.util.Map;

public record SelfieUploadResult(HttpStatus status, Map<String, Object> body) {

    public static SelfieUploadResult of(HttpStatus status, Map<String, Object> body) {
        return new SelfieUploadResult(status, body);
    }
}
