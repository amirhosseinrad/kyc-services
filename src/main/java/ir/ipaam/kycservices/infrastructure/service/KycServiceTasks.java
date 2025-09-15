package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;

public interface KycServiceTasks {

    // KYC Status
    KycProcessInstance checkKycStatus(String nationalCode);

    // Logging & Retry
    void logFailureAndRetry(String stepName, String reason, String processInstanceId);

    // Document / OCR
    void runOcrExtraction(Long documentId, String processInstanceId);

    void compareOcrWithIdentity(Long documentId, String nationalCode, String processInstanceId);

    // Fraud
    void runFraudCheck(Long documentId, String processInstanceId);

    // Address
    void validateZipCode(String zipCode, String processInstanceId);

    void storeAddress(String address, String zipCode, String processInstanceId);

    // Card / ID
    void storeCardTrackingNumber(String trackingNumber, String processInstanceId);
}
