package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ir.ipaam.kycservices.application.service.dto.BookletValidationData;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookletValidationResponse(
        @JsonProperty("result") Result result,
        @JsonProperty("status") ServiceStatus status
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("data") BookletValidationData data,
            @JsonProperty("status") ServiceStatus status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceStatus(
            @JsonProperty("code") Integer code,
            @JsonProperty("message") String message,
            @JsonProperty("description") String description
    ) {
    }
}
