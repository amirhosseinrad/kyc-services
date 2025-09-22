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
import java.util.NoSuchElementException;
import java.util.Locale;

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

    @BeforeEach
    void setUp() {
        service = new MinioStorageService(minioClient, "card", "id", "bio", "signature", imageBrandingService);
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

        verify(imageBrandingService).brand(any(byte[].class), eq("front.png"));
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
