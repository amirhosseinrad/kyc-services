package ir.ipaam.kycservices.domain.model.value;

import java.util.Arrays;
import java.util.Objects;

/**
 * Describes a binary payload that should be uploaded to the external storage service.
 */
public final class DocumentPayloadDescriptor {

    private final byte[] data;
    private final String filename;

    public DocumentPayloadDescriptor(byte[] data, String filename) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException("data must not be empty");
        }

        Objects.requireNonNull(filename, "filename must not be null");
        String normalizedFilename = filename.trim();
        if (normalizedFilename.isEmpty()) {
            throw new IllegalArgumentException("filename must not be blank");
        }

        this.data = Arrays.copyOf(data, data.length);
        this.filename = normalizedFilename;
    }

    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    public String filename() {
        return filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentPayloadDescriptor other)) {
            return false;
        }
        return Objects.equals(this.data, other.data)
                && Objects.equals(this.filename, other.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, filename);
    }

    @Override
    public String toString() {
        return "DocumentPayloadDescriptor[" +
                "data=" + data +
                ", filename=" + filename +
                ']';
    }
}
