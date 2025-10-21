package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardOcrBackResponse(@JsonProperty("result") Result result) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(@JsonProperty("data") CardOcrBackData data) {
    }
}
