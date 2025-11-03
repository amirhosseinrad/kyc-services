package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface CardStatusService {

    ResponseEntity<Map<String, Object>> updateCardStatus(CardStatusRequest request);
}
