package ir.ipaam.kycservices.application.service.dto;

public record VideoUploadResponse(
        String processInstanceId,
        Integer videoSize,
        Boolean match,
        Double livenessScore,
        Boolean isReal,
        String livenessTrackId,
        Integer framesCount,
        String status
) {
}
