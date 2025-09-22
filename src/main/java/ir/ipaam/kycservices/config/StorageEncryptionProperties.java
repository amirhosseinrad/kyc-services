package ir.ipaam.kycservices.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage.minio.encryption")
@Getter
@Setter
public class StorageEncryptionProperties {

    private boolean enabled = false;

    /**
     * Base64 encoded AES key. Must resolve to 16, 24 or 32 bytes when encryption is enabled.
     */
    private String key;

    @Min(1)
    private int ivLength = 12;
}
