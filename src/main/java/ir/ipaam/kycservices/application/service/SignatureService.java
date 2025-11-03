package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.SignatureUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface SignatureService {

    SignatureUploadResult uploadSignature(MultipartFile signature, String processInstanceId);
}
