package ir.ipaam.kycservices.application.api.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VideoService {

    ResponseEntity<Map<String, Object>> uploadVideo(MultipartFile video, String processInstanceId);
}
