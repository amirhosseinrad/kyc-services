package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.AddressVerificationRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface AddressService {

    ResponseEntity<Map<String, Object>> collectAddress(AddressVerificationRequest request, String stageHeader);
}

