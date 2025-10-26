package ir.ipaam.kycservices.application.service.dto;

/**
 * Represents the response payload returned by the booklet validation service.
 */
public record BookletValidationData(String trackId, String type, Integer rotation) {
}
