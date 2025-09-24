package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardStatusRequest(
        @NotBlank(message = "processInstanceId is required")
        String processInstanceId,
        @NotNull(message = "hasNewNationalCard is required")
        Boolean hasNewNationalCard
) {
}
