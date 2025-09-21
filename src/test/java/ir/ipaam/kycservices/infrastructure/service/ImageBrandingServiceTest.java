package ir.ipaam.kycservices.infrastructure.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageBrandingServiceTest {

    private final ImageBrandingService service = new ImageBrandingService();

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

        BufferedImage branded = ImageIO.read(new ByteArrayInputStream(result.data()));
        assertThat(branded.getWidth()).isGreaterThan(original.getWidth());
        assertThat(branded.getHeight()).isGreaterThan(original.getHeight());
    }

    @Test
    void brandSkipsUnsupportedFormats() {
        byte[] payload = new byte[]{1, 2, 3, 4};

        ImageBrandingService.BrandingResult result = service.brand(payload, "file.bin");

        assertThat(result.branded()).isFalse();
        assertThat(result.data()).containsExactly(payload);
    }
}
