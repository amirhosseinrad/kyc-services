package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.SelfieUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface SelfieService {

    SelfieUploadResult uploadSelfie(MultipartFile selfie, String processInstanceId);
}
