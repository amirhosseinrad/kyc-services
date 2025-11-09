package ir.ipaam.kycservices.application.api.error;

public class ObjectStorageUnavailableException extends IllegalStateException {

    public ObjectStorageUnavailableException(Throwable cause) {
        super(ErrorMessageKeys.STORAGE_UNAVAILABLE, cause);
    }
}
