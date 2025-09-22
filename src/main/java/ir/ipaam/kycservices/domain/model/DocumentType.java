package ir.ipaam.kycservices.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Enumeration of supported document types handled by the KYC services.
 */
public enum DocumentType {
    CARD_BACK,
    CARD_FRONT,
    ID_BOOKLET,
    PHOTO,
    VIDEO,
    SIGNATURE;

    @JsonCreator
    public static DocumentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DocumentType value must not be null or blank");
        }

        return DocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String getValue() {
        return name();
    }
}
