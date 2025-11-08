package ir.ipaam.kycservices.application.service.dto;

public record CardTrackingResponse(
        String processInstanceId,
        String trackingNumber,
        String status
) {
}
