package ir.ipaam.kycservices.infrastructure.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        service = new MinioStorageService(minioClient, "card", "id", "bio");
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
}
