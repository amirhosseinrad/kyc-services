package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardOcrFrontResponse(@JsonProperty("result") Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("data") CardOcrFrontData data) {
    }
}
