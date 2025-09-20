package ir.ipaam.kycservices.application.api.error;

public class FileProcessingException extends RuntimeException {

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileProcessingException(String message) {
        super(message);
    }
}
