package ir.ipaam.kycservices.application.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.ipaam.kycservices.domain.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DocumentQueryRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "nationalCode must be 10 digits")
        String nationalCode,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DocumentType documentType
) {
}
