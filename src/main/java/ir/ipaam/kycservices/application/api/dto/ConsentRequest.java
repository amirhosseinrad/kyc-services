package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsentRequest(
        @NotBlank(message = "processInstanceId is required")
        String processInstanceId,
        @NotBlank(message = "termsVersion is required")
        String termsVersion,
        boolean accepted
) {
}
