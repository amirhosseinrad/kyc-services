package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.dto.RecordTrackingNumberRequest;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadRequest;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResponse;
import ir.ipaam.kycservices.application.service.dto.CardStatusResponse;
import ir.ipaam.kycservices.application.service.dto.CardTrackingResponse;

public interface CardService {

    long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024L; // 2 MB

    CardDocumentUploadResponse uploadCardDocuments(CardDocumentUploadRequest request);

    CardStatusResponse updateCardStatus(CardStatusRequest request);

    CardTrackingResponse recordTrackingNumber(RecordTrackingNumberRequest request);
}
