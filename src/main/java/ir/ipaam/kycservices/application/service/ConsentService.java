package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.application.service.dto.ConsentResponse;

public interface ConsentService {

    ConsentResponse acceptConsent(ConsentRequest request);
}
