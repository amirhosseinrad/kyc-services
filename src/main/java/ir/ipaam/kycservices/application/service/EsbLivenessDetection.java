package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.LivenessCheckData;
import org.springframework.http.MediaType;

public interface EsbLivenessDetection {

    LivenessCheckData check(byte[] videoContent,
                            String videoFilename,
                            MediaType videoContentType,
                            byte[] imageContent,
                            String imageFilename,
                            MediaType imageContentType,
                            String referenceId);
}
