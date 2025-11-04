package ir.ipaam.kycservices.application.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.ipaam.kycservices.common.validation.IranianNationalCode;
import ir.ipaam.kycservices.domain.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentQueryRequest(
        @NotBlank
        @IranianNationalCode
        String nationalCode,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DocumentType documentType
) {
}
