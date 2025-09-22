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
    private static final double CARD_WIDTH_MM = 85.6;
    private static final double CARD_HEIGHT_MM = 53.98;
    private static final double MM_PER_INCH = 25.4;
    private static final int CARD_DPI = 300;
    static final int CARD_WIDTH_PX = (int) Math.round(CARD_WIDTH_MM / MM_PER_INCH * CARD_DPI);
    static final int CARD_HEIGHT_PX = (int) Math.round(CARD_HEIGHT_MM / MM_PER_INCH * CARD_DPI);
    private static final String BRANDING_PREFIX = "Uploaded by ToBank® KYC Service";
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
        int cardWidth = CARD_WIDTH_PX;
        int cardHeight = CARD_HEIGHT_PX;
        int shadowOffset = Math.max(1, Math.min(1, Math.round(Math.max(cardWidth, cardHeight) * 0.02f)));
        int cardSurfaceWidth = Math.max(1, cardWidth - shadowOffset);
        int cardSurfaceHeight = Math.max(1, cardHeight - shadowOffset);
        int cornerRadius = Math.max(24, Math.min(48, Math.round(Math.max(cardSurfaceWidth, cardSurfaceHeight) * 0.12f)));

        BufferedImage canvas = new BufferedImage(
                cardWidth,
                cardHeight,
                supportsAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            if (supportsAlpha) {
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, cardWidth, cardHeight);
                g2d.setComposite(AlphaComposite.SrcOver);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, cardWidth, cardHeight);
            }

            Color shadowColor = supportsAlpha ? new Color(0, 0, 0, 70) : new Color(210, 210, 210);
            g2d.setColor(shadowColor);
            g2d.fillRoundRect(shadowOffset, shadowOffset, cardSurfaceWidth, cardSurfaceHeight, cornerRadius, cornerRadius);

            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(0, 0, cardSurfaceWidth, cardSurfaceHeight, cornerRadius, cornerRadius);

            int topPadding = Math.max(10, Math.round(cardSurfaceHeight * 0.02f));
            int maxSidePadding = Math.max((cardSurfaceWidth - 1) / 2, 0);
            int sidePadding = Math.min(topPadding, maxSidePadding);
            sidePadding = Math.max(1/2, sidePadding/2);

            int bottomPadding = Math.max(50, Math.round(cardSurfaceHeight * 0.02f));
            int totalVerticalPadding = topPadding + bottomPadding;
            if (totalVerticalPadding >= cardSurfaceHeight) {
                int allowableTotal = Math.max(cardSurfaceHeight - 1, 1);
                int excess = totalVerticalPadding - allowableTotal;
                int topReduction = Math.min(topPadding - 1, (excess + 1) / 2);
                int bottomReduction = Math.min(bottomPadding - 1, excess / 2);
                topPadding = Math.max(1, topPadding - topReduction);
                bottomPadding = Math.max(1, bottomPadding - bottomReduction);
            }

            int imageAreaWidth = Math.max(1, cardSurfaceWidth - sidePadding * 2);
            int imageAreaHeight = Math.max(1, cardSurfaceHeight - topPadding - bottomPadding);

            float widthScale = imageAreaWidth / (float) original.getWidth();
            float heightScale = imageAreaHeight / (float) original.getHeight();
            float scale = Math.min(widthScale, heightScale);
            if (!Float.isFinite(scale) || scale <= 0f) {
                scale = 1f;
            }

            int scaledWidth = Math.max(1, Math.round(original.getWidth() * scale));
            int scaledHeight = Math.max(1, Math.round(original.getHeight() * scale));

            int imageX = sidePadding + (imageAreaWidth - scaledWidth) / 2;
            int imageY = topPadding + (imageAreaHeight - scaledHeight) / 2;

            g2d.drawImage(original, imageX, imageY, scaledWidth, scaledHeight, null);

            int textAreaTop = topPadding + imageAreaHeight;
            int textAreaHeight = Math.max(1, cardSurfaceHeight - textAreaTop);

            g2d.setColor(new Color(235, 235, 235));
            g2d.drawLine(sidePadding, textAreaTop, cardSurfaceWidth - sidePadding, textAreaTop);

            int fontSize = Math.max(18, Math.min(36, textAreaHeight - 32));
            if (fontSize > textAreaHeight) {
                fontSize = Math.max(12, textAreaHeight);
            }
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
            g2d.setFont(font);

            FontMetrics metrics = g2d.getFontMetrics(font);
            int textX = sidePadding;

            int verticalSpace = Math.max(0, textAreaHeight - metrics.getHeight());
            int textY = textAreaTop + verticalSpace / 2 + metrics.getAscent();
            int maxTextY = cardSurfaceHeight - metrics.getDescent();
            if (textY > maxTextY) {
                textY = maxTextY;
            }

            g2d.setColor(new Color(87, 87, 87));
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
