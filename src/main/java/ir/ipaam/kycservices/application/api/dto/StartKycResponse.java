package ir.ipaam.kycservices.application.api.dto;

public record StartKycResponse(
        String processInstanceId,
        String status
) {
}
