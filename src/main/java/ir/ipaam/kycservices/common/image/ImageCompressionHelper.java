package ir.ipaam.kycservices.common.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Utility class that compresses image payloads to meet a required upper bound.
 */
public final class ImageCompressionHelper {

    private static final float[] QUALITY_STEPS = new float[]{0.85f, 0.75f, 0.65f, 0.55f, 0.45f, 0.35f, 0.25f, 0.15f, 0.1f};
    private static final double[] SCALE_STEPS = new double[]{1.0d, 0.9d, 0.8d, 0.7d, 0.6d, 0.5d, 0.4d, 0.3d, 0.2d, 0.15d, 0.1d};

    private ImageCompressionHelper() {
        // utility class
    }

    /**
     * Attempts to reduce the provided image so that it does not exceed {@code maxSizeBytes}.
     * If the payload is already within the allowed limit the original byte array is returned.
     *
     * @param imageBytes   the original image bytes
     * @param maxSizeBytes the maximum allowed size
     * @return either the original or the processed image bytes
     * @throws IllegalArgumentException if the image cannot be processed
     */
    public static byte[] reduceToMaxSize(byte[] imageBytes, long maxSizeBytes) {
        if (imageBytes == null) {
            throw new IllegalArgumentException("imageBytes must not be null");
        }
        if (maxSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSizeBytes must be positive");
        }
        if (imageBytes.length <= maxSizeBytes) {
            return imageBytes;
        }

        BufferedImage source = read(imageBytes);
        BufferedImage rgbSource = ensureRgbImage(source);

        byte[] bestAttempt = imageBytes;
        for (double scale : SCALE_STEPS) {
            BufferedImage scaled = scale < 1.0d ? scale(rgbSource, scale) : rgbSource;
            for (float quality : QUALITY_STEPS) {
                byte[] candidate = writeJpeg(scaled, quality);
                if (candidate.length <= maxSizeBytes) {
                    return candidate;
                }
                if (candidate.length < bestAttempt.length) {
                    bestAttempt = candidate;
                }
            }
            rgbSource = scaled;
        }

        if (bestAttempt.length <= maxSizeBytes) {
            return bestAttempt;
        }

        throw new IllegalArgumentException("Unable to reduce image below " + maxSizeBytes + " bytes");
    }

    private static BufferedImage read(byte[] imageBytes) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new IllegalArgumentException("Image could not be decoded");
            }
            return bufferedImage;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to decode image", ex);
        }
    }

    private static BufferedImage ensureRgbImage(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = converted.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static BufferedImage scale(BufferedImage source, double factor) {
        int newWidth = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int newHeight = Math.max(1, (int) Math.round(source.getHeight() * factor));
        if (newWidth == source.getWidth() && newHeight == source.getHeight()) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, newWidth, newHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writers available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            imageOutput.flush();
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to encode image", ex);
        } finally {
            writer.dispose();
        }
    }
}
