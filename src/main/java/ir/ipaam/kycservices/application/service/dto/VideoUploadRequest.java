package ir.ipaam.kycservices.application.service.dto;

import org.springframework.web.multipart.MultipartFile;

public record VideoUploadRequest(
        MultipartFile video,
        MultipartFile image,
        String processInstanceId
) {
}
