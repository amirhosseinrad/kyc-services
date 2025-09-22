package ir.ipaam.kycservices.infrastructure.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService service;

    @Mock
    private ImageBrandingService imageBrandingService;

    @Mock
    private DocumentCryptoService documentCryptoService;

    @BeforeEach
    void setUp() {
        service = new MinioStorageService(minioClient, "card", "id", "bio", "signature", imageBrandingService, documentCryptoService);
        when(documentCryptoService.encrypt(any())).thenAnswer(invocation -> {
            byte[] plaintext = invocation.getArgument(0, byte[].class);
            return new DocumentCryptoService.EncryptionResult(plaintext.clone(), null, false);
        });
    }

    @Test
    void downloadReturnsObjectBytes() throws Exception {
        byte[] data = new byte[]{1, 2, 3, 4};
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn(data);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        byte[] result = service.download("bucket/path/to/object");

        assertThat(result).containsExactly(data);
    }

    @Test
    void downloadThrowsWhenObjectNotFound() throws Exception {
        ErrorResponseException errorResponseException = mock(ErrorResponseException.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(errorResponseException.errorResponse().code()).thenReturn("NoSuchKey");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(errorResponseException);

        assertThatThrownBy(() -> service.download("bucket/path"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void downloadRejectsInvalidStoragePath() {
        assertThatThrownBy(() -> service.download("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storagePath must contain bucket and object");
    }

    @Test
    void uploadAppliesBrandingForVisualDocuments() throws Exception {
        byte[] original = new byte[]{1, 2, 3};
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(original, "front.png");
        byte[] branded = new byte[]{9, 8, 7, 6};

        when(imageBrandingService.brand(any(byte[].class), eq("front.png")))
                .thenReturn(new ImageBrandingService.BrandingResult(branded, true, "png", null, null));
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        DocumentMetadata metadata = service.upload(descriptor, "PHOTO", "process-1");

        assertThat(metadata.isBranded()).isTrue();
        assertThat(metadata.getPath()).isEqualTo("bio/process-1/photo/front-png");
        assertThat(metadata.getHash()).isEqualTo(sha256Hex(branded));
        assertThat(metadata.isEncrypted()).isFalse();
        assertThat(metadata.getEncryptionIv()).isNull();

        verify(imageBrandingService).brand(any(byte[].class), eq("front.png"));
    }

    @Test
    void uploadEncryptsPayloadWhenEncryptionEnabled() throws Exception {
        byte[] original = new byte[]{1, 2, 3, 4};
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(original, "video.mp4");
        byte[] encrypted = new byte[]{9, 9, 9};
        byte[] iv = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

        when(documentCryptoService.encrypt(any())).thenReturn(
                new DocumentCryptoService.EncryptionResult(encrypted, iv, true));
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        DocumentMetadata metadata = service.upload(descriptor, "VIDEO", "process-99");

        assertThat(metadata.isEncrypted()).isTrue();
        assertThat(metadata.getEncryptionIv()).isEqualTo(Base64.getEncoder().encodeToString(iv));
        assertThat(metadata.getHash()).isEqualTo(sha256Hex(original));
    }

    @Test
    void downloadDecryptsEncryptedPayload() throws Exception {
        byte[] encrypted = new byte[]{5, 4, 3};
        byte[] decrypted = new byte[]{1, 2, 3};
        String base64Iv = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});

        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn(encrypted);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);
        when(documentCryptoService.decrypt(any(), any())).thenReturn(decrypted);

        byte[] result = service.download("bucket/object", true, base64Iv);

        assertThat(result).containsExactly(decrypted);
        verify(documentCryptoService).decrypt(any(), any());
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(data);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte b : hashed) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }
}
