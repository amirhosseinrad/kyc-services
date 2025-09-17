package ir.ipaam.kycservices.domain.model.value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes a binary payload that should be uploaded to the external storage service.
 */
public record DocumentPayloadDescriptor(byte[] data, String filename) {

    public DocumentPayloadDescriptor {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("data must not be empty");
        }

        Objects.requireNonNull(filename, "filename must not be null");
        String normalizedFilename = filename.trim();
        if (normalizedFilename.isEmpty()) {
            throw new IllegalArgumentException("filename must not be blank");
        }

        data = Arrays.copyOf(data, data.length);
        filename = normalizedFilename;
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
