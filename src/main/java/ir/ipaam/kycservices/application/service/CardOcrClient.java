package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;

/**
 * Client abstraction responsible for interacting with the external OCR service that extracts
 * textual information from national ID card images.
 */
public interface CardOcrClient {

    /**
     * Performs OCR on the front side of the ID card image.
     *
     * @param content  the binary payload of the card image
     * @param filename the original filename of the multipart upload (used when forwarding the request)
     * @return the extracted OCR details for the front side
     */
    CardOcrFrontData extractFront(byte[] content, String filename);

    /**
     * Performs OCR on the back side of the ID card image.
     *
     * @param content  the binary payload of the card image
     * @param filename the original filename of the multipart upload (used when forwarding the request)
     * @return the extracted OCR details for the back side
     */
    CardOcrBackData extractBack(byte[] content, String filename);
}
