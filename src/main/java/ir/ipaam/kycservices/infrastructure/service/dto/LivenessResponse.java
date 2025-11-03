package ir.ipaam.kycservices.infrastructure.service.dto;

public record LivenessResponse(
        ResponseStatus status,
        Result result,
        Meta meta) {

    public record ResponseStatus(Integer code, String message, String description) {
    }

    public record Result(LivenessData data, ResponseStatus status) {
    }

    public record LivenessData(String trackId, Video video) {
    }

    public record Video(Integer framesCount, Double liveness, Boolean isReal) {
    }

    public record Meta(String transactionId) {
    }
}
