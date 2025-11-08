package ir.ipaam.kycservices.application.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomerInfoRequest(
        @NotBlank(message = "processInstanceId is required")
        String processInstanceId,
        @NotBlank(message = "firstNameEn is required")
        String firstNameEn,
        @NotBlank(message = "lastNameEn is required")
        String lastNameEn,
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,
        @NotBlank(message = "telephone is required")
        String telephone
) {
}
