package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalizedMessage(String code, String en, String fa) {

    public LocalizedMessage {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
    }
}
