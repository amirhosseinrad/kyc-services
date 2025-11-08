package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.service.dto.EnglishPersonalInfoResponse;

public interface EnglishPersonalInfoService {

    EnglishPersonalInfoResponse provideEnglishPersonalInfo(EnglishPersonalInfoRequest request);
}
