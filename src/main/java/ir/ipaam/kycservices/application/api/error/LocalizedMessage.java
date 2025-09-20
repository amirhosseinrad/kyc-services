package ir.ipaam.kycservices.application.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalizedMessage(String en, String fa) {
}
