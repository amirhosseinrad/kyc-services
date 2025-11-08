package ir.ipaam.kycservices.application.service.dto;

public record EnglishPersonalInfoResponse(
        String processInstanceId,
        String firstNameEn,
        String lastNameEn,
        String email,
        String telephone,
        String status
) {
}
