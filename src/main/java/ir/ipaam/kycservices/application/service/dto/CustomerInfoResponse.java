package ir.ipaam.kycservices.application.service.dto;

public record CustomerInfoResponse(
        String processInstanceId,
        String firstNameEn,
        String lastNameEn,
        String email,
        String telephone,
        String status
) {
}
