package ir.ipaam.kycservices.infrastructure.service;

import java.util.List;

public interface KycUserTasks {

    // 1. Upload front/back of card (stored as BLOB)
    void uploadCardDocuments(byte[] frontImage, byte[] backImage, String processInstanceId);

    // 2. Upload signature (stored as BLOB)
    void uploadSignature(byte[] signatureImage, String processInstanceId);

    // 3. Consent & Declarations
    void acceptConsent(String termsVersion, boolean accepted, String processInstanceId);

    // 4. Collect Zip-Code and Address
    void provideAddress(String address, String zipCode, String processInstanceId);

    // 5. Upload selfie & live face video (stored as BLOBs)
    void uploadSelfie(byte[] selfie, String processInstanceId);

    void uploadVideo(byte[] video, String processInstanceId);

    // 6. Upload ID pages (1–4) (stored as BLOBs)
    void uploadIdPages(List<byte[]> pages, String processInstanceId);

    // 7. Upload selfie & video (from card branch)
    void uploadCardBranchSelfieAndVideo(byte[] photo, byte[] video, String processInstanceId);

    // 8. Provide English firstname, lastname, email, telephone
    void provideEnglishPersonalInfo(String firstNameEn, String lastNameEn, String email, String telephone, String processInstanceId);
}
