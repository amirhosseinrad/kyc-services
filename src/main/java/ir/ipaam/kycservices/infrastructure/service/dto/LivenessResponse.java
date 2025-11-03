package ir.ipaam.kycservices.infrastructure.service.dto;

public record LivenessResponse(
        ResponseStatus status,
        Result result,
        Meta meta) {

    public record ResponseStatus(Integer code, String message, String description) {
    }

    public record Result(LivenessData data, ResponseStatus status) {
    }

    public record LivenessData(String trackId,
                               Image image1,
                               Image image2,
                               Video video,
                               Boolean authenticated) {
    }

    public record Image(Face face, Double similarity, Boolean verified) {
    }

    public record Video(Integer framesCount,
                        Face face,
                        Double similarity,
                        Boolean verified,
                        Double liveness,
                        Boolean isReal) {
    }

    public record Face(FacePosition position, Double confidence) {
    }

    public record FacePosition(Integer x, Integer y, Integer width, Integer height) {
    }

    public record Meta(String transactionId) {
    }
}
