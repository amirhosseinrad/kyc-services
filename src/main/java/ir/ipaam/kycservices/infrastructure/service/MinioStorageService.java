package ir.ipaam.kycservices.infrastructure.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final String cardBucket;
    private final String idBucket;
    private final String biometricBucket;
    private final String signatureBucket;
    private final ImageBrandingService imageBrandingService;
    private final Set<String> ensuredBuckets = ConcurrentHashMap.newKeySet();

    public MinioStorageService(
            MinioClient minioClient,
            @Value("${storage.minio.bucket.card}") String cardBucket,
            @Value("${storage.minio.bucket.id}") String idBucket,
            @Value("${storage.minio.bucket.biometric}") String biometricBucket,
            @Value("${storage.minio.bucket.signature}") String signatureBucket,
            ImageBrandingService imageBrandingService) {
        this.minioClient = minioClient;
        this.cardBucket = cardBucket;
        this.idBucket = idBucket;
        this.biometricBucket = biometricBucket;
        this.signatureBucket = signatureBucket;
        this.imageBrandingService = imageBrandingService;
    }

    public DocumentMetadata upload(DocumentPayloadDescriptor descriptor, String documentType, String processInstanceId) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("documentType must not be blank");
        }
        byte[] data = descriptor.data();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("descriptor data must not be empty");
        }

        boolean branded = false;
        if (shouldApplyBranding(documentType)) {
            ImageBrandingService.BrandingResult result = imageBrandingService.brand(data, descriptor.filename());
            if (result.data() != null && result.data().length > 0) {
                data = result.data();
            }
            branded = result.branded();
            if (branded) {
                log.debug("Applied branding for document {} ({})", documentType, descriptor.filename());
            } else {
                log.debug("Branding skipped or failed for document {} ({})", documentType, descriptor.filename());
            }
        }

        String bucket = determineBucket(documentType);
        String objectName = buildObjectName(processInstanceId, documentType, descriptor.filename());

        ensureBucketExists(bucket);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(stream, data.length, -1)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed to upload {} for process {} to bucket {}", documentType, processInstanceId, bucket, ex);
            throw new IllegalStateException("Failed to upload document to object storage", ex);
        }

        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setPath(bucket + "/" + objectName);
        metadata.setHash(hash(data));
        metadata.setBranded(branded);
        return metadata;
    }

    public byte[] download(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("storagePath must not be blank");
        }

        int separator = storagePath.indexOf('/');
        if (separator <= 0 || separator >= storagePath.length() - 1) {
            throw new IllegalArgumentException("storagePath must contain bucket and object");
        }

        String bucket = storagePath.substring(0, separator);
        String objectName = storagePath.substring(separator + 1);

        try (GetObjectResponse response = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
            return response.readAllBytes();
        } catch (ErrorResponseException ex) {
            String code = ex.errorResponse() != null ? ex.errorResponse().code() : null;
            if ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code)) {
                throw new NoSuchElementException("Object not found in storage");
            }
            throw new IllegalStateException("Failed to download object from storage", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download object from storage", ex);
        }
    }

    private String determineBucket(String documentType) {
        if (documentType.startsWith("CARD_")) {
            return cardBucket;
        }
        if ("SIGNATURE".equals(documentType)) {
            return signatureBucket;
        }
        if (documentType.startsWith("ID_PAGE_")) {
            return idBucket;
        }
        if ("PHOTO".equals(documentType) || "VIDEO".equals(documentType)) {
            return biometricBucket;
        }
        throw new IllegalArgumentException("Unsupported document type: " + documentType);
    }

    private boolean shouldApplyBranding(String documentType) {
        if (documentType == null) {
            return false;
        }
        return documentType.startsWith("CARD_")
                || documentType.startsWith("ID_PAGE_")
                || "PHOTO".equals(documentType)
                || "SIGNATURE".equals(documentType);
    }

    private String buildObjectName(String processInstanceId, String documentType, String filename) {
        String safeProcessId = (processInstanceId == null || processInstanceId.isBlank())
                ? "unknown-process"
                : sanitize(processInstanceId);
        String typeSegment = sanitize(documentType.toLowerCase(Locale.ROOT).replace('_', '-'));
        String safeFilename = (filename == null || filename.isBlank())
                ? typeSegment + "-" + UUID.randomUUID()
                : sanitizeFilename(filename);
        return safeProcessId + "/" + typeSegment + "/" + safeFilename;
    }

    private String sanitize(String value) {
        String trimmed = value.trim();
        return trimmed.replaceAll("[^a-zA-Z0-9\\-_/]", "-");
    }

    private String sanitizeFilename(String filename) {
        String normalized = filename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String justName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String sanitized = sanitize(justName);
        if (sanitized.isBlank()) {
            return "file-" + UUID.randomUUID();
        }
        return sanitized;
    }

    private void ensureBucketExists(String bucket) {
        if (ensuredBuckets.contains(bucket)) {
            return;
        }
        synchronized (ensuredBuckets) {
            if (ensuredBuckets.contains(bucket)) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                ensuredBuckets.add(bucket);
            } catch (Exception ex) {
                log.error("Failed to ensure bucket {} exists", bucket, ex);
                throw new IllegalStateException("Failed to ensure bucket existence", ex);
            }
        }
    }

    private String hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(data);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
