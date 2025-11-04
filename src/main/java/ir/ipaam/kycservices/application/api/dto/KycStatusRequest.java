package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.common.validation.IranianNationalCode;
import jakarta.validation.constraints.NotBlank;

public record KycStatusRequest(
        @NotBlank(message = "nationalCode is required")
        @IranianNationalCode
        String nationalCode
) {}
