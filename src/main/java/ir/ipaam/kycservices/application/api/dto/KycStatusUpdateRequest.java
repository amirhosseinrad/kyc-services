package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;

public record KycStatusUpdateRequest(
        @NotBlank(message = "status is required")
        String status,
        @NotBlank(message = "stepName is required")
        String stepName,
        @NotBlank(message = "state is required")
        String state
) {}
