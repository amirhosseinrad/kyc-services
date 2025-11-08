package ir.ipaam.kycservices.application.service.dto;

public record CardDocumentUploadResponse(
        String processInstanceId,
        Integer frontImageSize,
        Integer backImageSize,
        String status
) {
}
