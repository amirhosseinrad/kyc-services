package ir.ipaam.kycservices.application.service.dto;

public record LivenessCheckData(
        String trackId,
        Integer framesCount,
        Double livenessScore,
        Boolean isReal) {
}
