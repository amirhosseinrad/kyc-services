package ir.ipaam.kycservices.infrastructure.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ImageBrandingService {

    private static final Logger log = LoggerFactory.getLogger(ImageBrandingService.class);
    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpeg", "jpg");
    private static final String BRANDING_PREFIX = "Uploaded by KYC Service";
    private static final DateTimeFormatter BRANDING_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final Clock clock;

    public ImageBrandingService() {
        this(Clock.systemDefaultZone());
    }

    public ImageBrandingService(Clock clock) {
        this.clock = clock;
    }

    public BrandingResult brand(byte[] payload, String filename) {
        if (payload == null || payload.length == 0) {
            byte[] safePayload = payload == null ? new byte[0] : payload;
            return BrandingResult.unmodified(safePayload, null);
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(payload);
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {

            if (imageInputStream == null) {
                log.debug("Could not create image stream for {}", filename);
                return BrandingResult.unmodified(payload, null);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                log.debug("No image reader available for {}", filename);
                return BrandingResult.unmodified(payload, null);
            }

            ImageReader reader = readers.next();
            String formatName = normalizeFormat(reader.getFormatName());
            if (!SUPPORTED_FORMATS.contains(formatName)) {
                log.debug("Skipping branding for {} due to unsupported format {}", filename, formatName);
                reader.dispose();
                return BrandingResult.unmodified(payload, formatName);
            }

            reader.setInput(imageInputStream, true);
            BufferedImage original = reader.read(0);
            reader.dispose();

            if (original == null) {
                log.warn("Image reader returned null for {}", filename);
                return BrandingResult.unmodified(payload, formatName);
            }

            boolean supportsAlpha = supportsAlpha(formatName);
            String brandingLabel = buildBrandingLabel();
            BufferedImage branded = redrawWithBranding(original, supportsAlpha, brandingLabel);

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                boolean written = ImageIO.write(branded, formatName, baos);
                if (!written) {
                    log.warn("ImageIO failed to encode {} as {}", filename, formatName);
                    return BrandingResult.unmodified(payload, formatName);
                }
                return new BrandingResult(baos.toByteArray(), true, formatName, brandingLabel);
            }
        } catch (Exception ex) {
            log.warn("Failed to brand image {}: {}", filename, ex.getMessage());
            log.debug("Branding failure", ex);
            return BrandingResult.unmodified(payload, null);
        }
    }

    private String buildBrandingLabel() {
        LocalDateTime now = LocalDateTime.now(clock);
        return String.format("%s - %s", BRANDING_PREFIX, now.format(BRANDING_FORMATTER));
    }

    private String normalizeFormat(String formatName) {
        if (formatName == null) {
            return "";
        }
        String normalized = formatName.toLowerCase(Locale.ROOT);
        if (Objects.equals(normalized, "jpg")) {
            return "jpeg";
        }
        return normalized;
    }

    private boolean supportsAlpha(String formatName) {
        return "png".equals(formatName);
    }

    private BufferedImage redrawWithBranding(BufferedImage original, boolean supportsAlpha, String brandingLabel) {
        int sidePadding = Math.max(32, Math.round(original.getWidth() * 0.08f));
        int topPadding = Math.max(28, Math.round(original.getHeight() * 0.08f));
        int bottomPadding = Math.max(96, Math.round(original.getHeight() * 0.18f));
        int cardWidth = original.getWidth() + sidePadding * 2;
        int cardHeight = original.getHeight() + topPadding + bottomPadding;
        int shadowOffset = Math.max(10, Math.min(18, Math.round(Math.max(original.getWidth(), original.getHeight()) * 0.05f)));
        int cornerRadius = Math.max(24, Math.min(48, Math.round(Math.max(original.getWidth(), original.getHeight()) * 0.12f)));

        int canvasWidth = cardWidth + shadowOffset;
        int canvasHeight = cardHeight + shadowOffset;

        BufferedImage canvas = new BufferedImage(
                canvasWidth,
                canvasHeight,
                supportsAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (supportsAlpha) {
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, canvasWidth, canvasHeight);
                g2d.setComposite(AlphaComposite.SrcOver);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, canvasWidth, canvasHeight);
            }

            Color shadowColor = supportsAlpha ? new Color(0, 0, 0, 70) : new Color(210, 210, 210);
            g2d.setColor(shadowColor);
            g2d.fillRoundRect(shadowOffset, shadowOffset, cardWidth, cardHeight, cornerRadius, cornerRadius);

            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(0, 0, cardWidth, cardHeight, cornerRadius, cornerRadius);

            g2d.drawImage(original, sidePadding, topPadding, null);

            int textAreaTop = topPadding + original.getHeight();
            int textAreaHeight = bottomPadding;

            g2d.setColor(new Color(235, 235, 235));
            g2d.drawLine(sidePadding, textAreaTop, cardWidth - sidePadding, textAreaTop);

            int fontSize = Math.max(18, Math.min(36, textAreaHeight - 32));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            g2d.setFont(font);

            FontMetrics metrics = g2d.getFontMetrics(font);
            int textWidth = metrics.stringWidth(brandingLabel);
            int textX = (cardWidth - textWidth) / 2;
            int minTextX = sidePadding;
            int maxTextX = cardWidth - sidePadding - textWidth;
            if (textX < minTextX) {
                textX = minTextX;
            }
            if (textX > maxTextX) {
                textX = maxTextX;
            }

            int textY = textAreaTop + ((textAreaHeight - metrics.getHeight()) / 2) + metrics.getAscent();

            g2d.setColor(new Color(66, 133, 244));
            g2d.drawString(brandingLabel, textX, textY);
        } finally {
            g2d.dispose();
        }
        return canvas;
    }

    public record BrandingResult(byte[] data, boolean branded, String format, String label) {
        public static BrandingResult unmodified(byte[] data, String format) {
            return new BrandingResult(data, false, format, null);
        }
    }
}
