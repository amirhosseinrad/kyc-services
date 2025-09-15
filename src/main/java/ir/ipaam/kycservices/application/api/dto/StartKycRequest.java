package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StartKycRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "nationalCode must be 10 digits")
        String nationalCode
) {}
