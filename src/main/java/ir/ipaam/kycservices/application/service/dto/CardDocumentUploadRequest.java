package ir.ipaam.kycservices.application.service.dto;

import org.springframework.web.multipart.MultipartFile;

public record CardDocumentUploadRequest(
        MultipartFile frontImage,
        MultipartFile backImage,
        String processInstanceId
) {
}
