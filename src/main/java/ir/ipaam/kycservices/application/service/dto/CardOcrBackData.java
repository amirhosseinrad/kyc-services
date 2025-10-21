package ir.ipaam.kycservices.application.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardOcrBackData(
        String trackId,
        String serialNumber,
        String barcode,
        Boolean ocrSuccess,
        Integer rotation
) {
}
