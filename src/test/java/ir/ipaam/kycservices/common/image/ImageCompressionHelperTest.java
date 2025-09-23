package ir.ipaam.kycservices.common.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageCompressionHelperTest {

    @Test
    void reduceToMaxSizeShrinksLargeImage() throws IOException {
        byte[] noisyImage = createNoisyPng(1200, 1200);
        long limit = 250_000L;
        assertThat(noisyImage.length).isGreaterThan((int) limit);

        byte[] reduced = ImageCompressionHelper.reduceToMaxSize(noisyImage, limit);

        assertThat(reduced.length).isLessThanOrEqualTo((int) limit);
        assertThat(reduced.length).isLessThan(noisyImage.length);
    }

    @Test
    void reduceToMaxSizeThrowsWhenLimitUnreachable() throws IOException {
        byte[] noisyImage = createNoisyPng(300, 300);
        long limit = 50L;
        assertThat(noisyImage.length).isGreaterThan((int) limit);

        assertThatThrownBy(() -> ImageCompressionHelper.reduceToMaxSize(noisyImage, limit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to reduce image below");
    }

    private byte[] createNoisyPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)).getRGB();
                image.setRGB(x, y, rgb);
            }
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
