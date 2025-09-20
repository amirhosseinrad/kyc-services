package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorCode code, LocalizedMessage message, Map<String, ?> details) {

    public ErrorResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ErrorResponse of(ErrorCode code, LocalizedMessage message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse of(ErrorCode code, LocalizedMessage message, Map<String, ?> details) {
        return new ErrorResponse(code, message, details);
    }
}
