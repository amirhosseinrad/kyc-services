package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.AddressVerificationRequest;
import ir.ipaam.kycservices.application.service.dto.AddressCollectionResponse;

public interface AddressService {

    AddressCollectionResponse collectAddress(AddressVerificationRequest request, String stageHeader);
}
