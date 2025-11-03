package ir.ipaam.kycservices.infrastructure.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FaceDetectionResponse(
        @JsonProperty("result") Result result,
        @JsonProperty("status") ServiceStatus status,
        @JsonProperty("meta") Meta meta) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("data") Data data,
            @JsonProperty("status") ServiceStatus status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("trackId") String trackId,
            @JsonProperty("faces") List<Face> faces) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Face(
            @JsonProperty("position") Position position,
            @JsonProperty("confidence") Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(
            @JsonProperty("x") Integer x,
            @JsonProperty("y") Integer y,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceStatus(
            @JsonProperty("code") Integer code,
            @JsonProperty("message") String message,
            @JsonProperty("description") String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            @JsonProperty("transactionId") String transactionId) {
    }
}
