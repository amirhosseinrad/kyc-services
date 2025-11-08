package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.CustomerInfoRequest;
import ir.ipaam.kycservices.application.service.dto.CustomerInfoResponse;

public interface CustomerInfoService {

    CustomerInfoResponse provideCustomerInfo(CustomerInfoRequest request);
}
