package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.LivenessCheckData;
import org.springframework.http.MediaType;

public interface EsbLivenessDetection {

    LivenessCheckData check(byte[] content, String filename, MediaType contentType, String referenceId);
}
