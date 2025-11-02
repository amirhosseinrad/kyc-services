package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.BookletValidationData;
import org.springframework.http.MediaType;

/**
 * Validates uploaded booklet pages via the external OCR/validation service.
 */
public interface EsbBookletValidation {

    /**
     * Validates the provided booklet page.
     *
     * @param content  binary payload of the booklet page image
     * @param filename original filename of the booklet page
     * @param contentType detected media type of the page, or {@code null} if unknown
     * @return validation metadata returned by the upstream service
     */
    BookletValidationData validate(byte[] content, String filename, MediaType contentType);
}
