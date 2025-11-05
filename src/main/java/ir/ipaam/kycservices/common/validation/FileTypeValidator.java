package ir.ipaam.kycservices.common.validation;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * Centralised helper for validating multipart file types.
 */
public final class FileTypeValidator {

    public static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    public static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf");
    public static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    public static final Set<String> VIDEO_CONTENT_TYPES = Set.of("video/mp4", "video/quicktime");
    public static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov");

    private FileTypeValidator() {
    }

    public static void ensureAllowedType(MultipartFile file,
                                         Set<String> allowedContentTypes,
                                         Set<String> allowedExtensions,
                                         String errorKey) {
        if (file == null) {
            return;
        }

        if (!isExtensionAllowed(file.getOriginalFilename(), allowedExtensions)) {
            throw new IllegalArgumentException(errorKey);
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !isContentTypeAllowed(contentType, allowedContentTypes)) {
            throw new IllegalArgumentException(errorKey);
        }
    }

    private static boolean isExtensionAllowed(String filename, Set<String> allowedExtensions) {
        if (allowedExtensions == null || allowedExtensions.isEmpty() || !StringUtils.hasText(filename)) {
            return true;
        }
        String extension = extractExtension(filename);
        return allowedExtensions.contains(extension);
    }

    private static boolean isContentTypeAllowed(String contentType, Set<String> allowedContentTypes) {
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            return true;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (allowedContentTypes.contains(normalized)) {
            return true;
        }
        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 0) {
            String wildcard = normalized.substring(0, slashIndex) + "/*";
            return allowedContentTypes.contains(wildcard);
        }
        return false;
    }

    private static String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }
}
