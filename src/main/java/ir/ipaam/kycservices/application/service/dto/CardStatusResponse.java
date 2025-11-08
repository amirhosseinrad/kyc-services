package ir.ipaam.kycservices.application.service.dto;

public record CardStatusResponse(
        String processInstanceId,
        Boolean hasNewNationalCard,
        String status
) {
}
