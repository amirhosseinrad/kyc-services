package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Machine readable error codes exposed by the public API.
 */
public enum ErrorCode {

    VALIDATION_FAILED("KYC-001"),
    RESOURCE_NOT_FOUND("KYC-002"),
    COMMAND_REJECTED("KYC-003"),
    FILE_PROCESSING_FAILED("KYC-004"),
    INQUIRY_SERVICE_UNAVAILABLE("KYC-005"),
    UNEXPECTED_ERROR("KYC-999");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
