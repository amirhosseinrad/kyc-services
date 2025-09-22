package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.config.StorageEncryptionProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCryptoServiceTest {

    @Test
    void encryptReturnsPlaintextWhenDisabled() {
        StorageEncryptionProperties properties = new StorageEncryptionProperties();
        properties.setEnabled(false);

        DocumentCryptoService service = new DocumentCryptoService(properties);
        service.init();

        byte[] plaintext = "hello".getBytes(StandardCharsets.UTF_8);
        DocumentCryptoService.EncryptionResult result = service.encrypt(plaintext);

        assertThat(result.encrypted()).isFalse();
        assertThat(result.initializationVector()).isNull();
        assertThat(result.payload()).isEqualTo(plaintext);
    }

    @Test
    void encryptAndDecryptRoundTrip() {
        StorageEncryptionProperties properties = enabledProperties();

        DocumentCryptoService service = new DocumentCryptoService(properties);
        service.init();

        byte[] plaintext = "sensitive-data".getBytes(StandardCharsets.UTF_8);
        DocumentCryptoService.EncryptionResult result = service.encrypt(plaintext);

        assertThat(result.encrypted()).isTrue();
        assertThat(result.initializationVector()).hasSize(properties.getIvLength());
        assertThat(result.payload()).isNotEqualTo(plaintext);

        byte[] decrypted = service.decrypt(result.payload(), result.initializationVector());
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void decryptRejectsMissingInitializationVector() {
        StorageEncryptionProperties properties = enabledProperties();

        DocumentCryptoService service = new DocumentCryptoService(properties);
        service.init();

        assertThatThrownBy(() -> service.decrypt(new byte[]{1, 2, 3}, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Initialization vector must be provided");
    }

    private StorageEncryptionProperties enabledProperties() {
        StorageEncryptionProperties properties = new StorageEncryptionProperties();
        properties.setEnabled(true);
        properties.setIvLength(12);
        byte[] key = "0123456789ABCDEF0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        properties.setKey(Base64.getEncoder().encodeToString(key));
        return properties;
    }
}
