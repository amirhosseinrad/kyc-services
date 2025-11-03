package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.FaceDetectionData;
import org.springframework.http.MediaType;

public interface EsbFaceDetection {

    FaceDetectionData detect(byte[] content, String filename, MediaType contentType, String referenceId);
}
