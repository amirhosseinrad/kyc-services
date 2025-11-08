package ir.ipaam.kycservices.application.service.dto;

public record ConsentResponse(
        String processInstanceId,
        String termsVersion,
        boolean accepted,
        String status
) {
}
