package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface EnglishPersonalInfoService {

    ResponseEntity<Map<String, Object>> provideEnglishPersonalInfo(EnglishPersonalInfoRequest request);
}

