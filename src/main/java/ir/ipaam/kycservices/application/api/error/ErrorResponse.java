package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(LocalizedMessage error, Map<String, ?> details) {

    public ErrorResponse {
        Objects.requireNonNull(error, "error must not be null");
    }

    public static ErrorResponse of(LocalizedMessage error) {
        return new ErrorResponse(error, null);
    }

    public static ErrorResponse of(LocalizedMessage error, Map<String, ?> details) {
        return new ErrorResponse(error, details);
    }
}
