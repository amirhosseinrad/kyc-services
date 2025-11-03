package ir.ipaam.kycservices.application.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VideoService {

    ResponseEntity<Map<String, Object>> uploadVideo(MultipartFile video, MultipartFile image, String processInstanceId);
}
