package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DocumentQueryRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "nationalCode must be 10 digits")
        String nationalCode,

        @NotBlank
        String documentType
) {
}
