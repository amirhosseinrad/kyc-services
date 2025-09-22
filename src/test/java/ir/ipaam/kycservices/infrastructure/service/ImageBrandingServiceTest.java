package ir.ipaam.kycservices.infrastructure.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ImageBrandingServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2024-05-12T10:15:00Z"), ZoneOffset.UTC);

    private final ImageBrandingService service = new ImageBrandingService(FIXED_CLOCK);

    @Test
    void brandAddsBorderAndMarkForPng() throws Exception {
        BufferedImage original = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = original.createGraphics();
        try {
            graphics.setColor(Color.ORANGE);
            graphics.fillRect(0, 0, original.getWidth(), original.getHeight());
        } finally {
            graphics.dispose();
        }

        byte[] payload;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(original, "png", baos);
            payload = baos.toByteArray();
        }

        ImageBrandingService.BrandingResult result = service.brand(payload, "test.png");

        assertThat(result.branded()).isTrue();
        assertThat(result.data()).isNotNull();
        assertThat(result.data().length).isGreaterThan(payload.length);
        assertThat(result.label()).isEqualTo("Uploaded by KYC Service - 2024-05-12 10:15");

        BufferedImage branded = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertThat(branded.getWidth()).isEqualTo(ImageBrandingService.CARD_WIDTH_PX);
        assertThat(branded.getHeight()).isEqualTo(ImageBrandingService.CARD_HEIGHT_PX);

        int shadowOffset = Math.max(1, Math.min(1, Math.round(Math.max(
                ImageBrandingService.CARD_WIDTH_PX, ImageBrandingService.CARD_HEIGHT_PX) * 0.02f)));
        int cardSurfaceWidth = Math.max(1, ImageBrandingService.CARD_WIDTH_PX - shadowOffset);
        int cardSurfaceHeight = Math.max(1, ImageBrandingService.CARD_HEIGHT_PX - shadowOffset);

        int topPadding = Math.max(10, Math.round(cardSurfaceHeight * 0.02f));
        int maxSidePadding = Math.max((cardSurfaceWidth - 1) / 2, 0);
        int imageSidePadding = Math.min(topPadding, maxSidePadding);
        imageSidePadding = Math.max(1 / 2, imageSidePadding / 2);
        int brandingSidePadding = Math.max(1, Math.round(imageSidePadding / 2f));

        int bottomPadding = Math.max(50, Math.round(cardSurfaceHeight * 0.02f));
        int imageAreaHeight = Math.max(1, cardSurfaceHeight - topPadding - bottomPadding);
        int textAreaTop = topPadding + imageAreaHeight;

        Color dividerColor = new Color(235, 235, 235);
        assertThat(new Color(branded.getRGB(brandingSidePadding, textAreaTop), true))
                .isEqualTo(dividerColor);
        assertThat(new Color(branded.getRGB(brandingSidePadding - 1, textAreaTop), true))
                .isEqualTo(Color.WHITE);

        int firstTextPixelX = Integer.MAX_VALUE;
        outerLoop:
        for (int y = textAreaTop + 5; y < cardSurfaceHeight; y++) {
            for (int x = 0; x < cardSurfaceWidth; x++) {
                if (branded.getRGB(x, y) != Color.WHITE.getRGB()) {
                    firstTextPixelX = x;
                    break outerLoop;
                }
            }
        }

        assertThat(firstTextPixelX).isEqualTo(brandingSidePadding);
    }

    @Test
    void brandSkipsUnsupportedFormats() {
        byte[] payload = new byte[]{1, 2, 3, 4};

        ImageBrandingService.BrandingResult result = service.brand(payload, "file.bin");

        assertThat(result.branded()).isFalse();
        assertThat(result.data()).containsExactly(payload);
        assertThat(result.label()).isNull();
    }
}
