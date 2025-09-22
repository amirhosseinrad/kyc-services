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
