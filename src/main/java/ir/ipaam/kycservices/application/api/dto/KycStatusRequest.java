package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KycStatusRequest(
        @NotBlank(message = "nationalCode is required")
        String nationalCode
) {}
