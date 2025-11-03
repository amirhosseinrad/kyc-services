package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.AddressVerificationRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface AddressVerificationService {

    ResponseEntity<Map<String, Object>> collectAddress(AddressVerificationRequest request, String stageHeader);
}

