package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.config.StorageEncryptionProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

@Service
public class DocumentCryptoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCryptoService.class);
    private static final int GCM_TAG_LENGTH = 128;

    private final StorageEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    private boolean encryptionEnabled;
    private SecretKey secretKey;
    private int ivLength;

    public DocumentCryptoService(StorageEncryptionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        this.encryptionEnabled = properties.isEnabled();
        this.ivLength = Math.max(1, properties.getIvLength());

        if (!encryptionEnabled) {
            log.info("Document storage encryption is disabled");
            return;
        }

        byte[] keyBytes = decodeKey(properties.getKey());
        validateKeyLength(keyBytes);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("Document storage encryption is enabled using AES-GCM (IV length: {} bytes)", ivLength);
    }

    public EncryptionResult encrypt(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");

        if (!encryptionEnabled) {
            return new EncryptionResult(plaintext.clone(), null, false);
        }

        byte[] iv = new byte[ivLength];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plaintext);
            return new EncryptionResult(cipherText, iv, true);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt document payload", ex);
        }
    }

    public byte[] decrypt(byte[] ciphertext, byte[] iv) {
        Objects.requireNonNull(ciphertext, "ciphertext must not be null");

        if (!encryptionEnabled) {
            return ciphertext.clone();
        }

        if (iv == null || iv.length == 0) {
            throw new IllegalArgumentException("Initialization vector must be provided for decryption");
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt document payload", ex);
        }
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    private byte[] decodeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Encryption key must be configured when encryption is enabled");
        }

        try {
            return Base64.getDecoder().decode(key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Encryption key must be Base64 encoded", ex);
        }
    }

    private void validateKeyLength(byte[] keyBytes) {
        int length = keyBytes.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalStateException("Invalid AES key length: " + length + " bytes");
        }
    }

    public record EncryptionResult(byte[] payload, byte[] initializationVector, boolean encrypted) {
        public EncryptionResult {
            payload = payload != null ? payload.clone() : null;
            initializationVector = initializationVector != null ? initializationVector.clone() : null;
        }
    }
}
