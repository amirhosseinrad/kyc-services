package ir.ipaam.kycservices.application.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CardOcrFrontData(
        String trackId,
        String nin,
        String firstName,
        String lastName,
        String dateOfBirth,
        String fatherName,
        String dateOfExpiration,
        Integer rotation
) {
}
