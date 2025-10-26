package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.BookletValidationData;

/**
 * Validates uploaded booklet pages via the external OCR/validation service.
 */
public interface BookletValidationClient {

    /**
     * Validates the provided booklet page.
     *
     * @param content  binary payload of the booklet page image
     * @param filename original filename of the booklet page
     * @return validation metadata returned by the upstream service
     */
    BookletValidationData validate(byte[] content, String filename);
}
