package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface ConsentService {

    ResponseEntity<Map<String, Object>> acceptConsent(ConsentRequest request);
}
