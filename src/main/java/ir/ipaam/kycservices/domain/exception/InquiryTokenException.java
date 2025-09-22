package ir.ipaam.kycservices.domain.exception;

public class InquiryTokenException extends RuntimeException {

    public InquiryTokenException(String messageKey) {
        super(messageKey);
    }

    public InquiryTokenException(String messageKey, Throwable cause) {
        super(messageKey, cause);
    }
}

