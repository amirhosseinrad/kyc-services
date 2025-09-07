package ir.ipaam.kycservices.infrastructure.service;

public interface KycServiceTasks {

    // National Code
    void validateNationalCodeChecksum(String nationalCode, String processInstanceId);

    void callNationalRegistry(String nationalCode, String processInstanceId);

    // OTP
    void sendOtp(String mobile, String processInstanceId);

    void checkOtp(String mobile, String otpCode, String processInstanceId);

    // Mobile / Shahkar
    void checkMobileFormat(String mobile, String processInstanceId);

    void callShahkarService(String nationalCode, String mobile, String processInstanceId);

    // Customer History
    void checkCustomerHistory(String nationalCode, String processInstanceId);

    // KYC Status
    void updateKycStatus(String processInstanceId, String status);

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
