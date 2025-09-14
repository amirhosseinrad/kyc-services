package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KycStatusUpdateRequest(
        @NotBlank(message = "status is required")
        String status
) {}
