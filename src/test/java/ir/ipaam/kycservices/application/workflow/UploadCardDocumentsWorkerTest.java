package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UploadCardDocumentsWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final UploadCardDocumentsWorker worker = new UploadCardDocumentsWorker(kycUserTasks);

    @Test
    void handleDelegatesToService() {
        byte[] frontBytes = "front".getBytes();
        byte[] backBytes = "back".getBytes();

        Map<String, Object> variables = Map.of(
                "frontImage", Base64.getEncoder().encodeToString(frontBytes),
                "backImage", Base64.getEncoder().encodeToString(backBytes),
                "processInstanceId", "proc-1"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadCardDocuments(frontBytes, backBytes, "proc-1");
        assertTrue((Boolean) result.get("cardDocumentsUploaded"));
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of(
                "backImage", Base64.getEncoder().encodeToString("data".getBytes()),
                "processInstanceId", "proc-1"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handleRejectsOversizedPayload() {
        byte[] large = new byte[(int) UploadCardDocumentsWorker.MAX_IMAGE_SIZE_BYTES + 1];
        Map<String, Object> variables = Map.of(
                "frontImage", Base64.getEncoder().encodeToString("front".getBytes()),
                "backImage", Base64.getEncoder().encodeToString(large),
                "processInstanceId", "proc-2"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handleDownscalesOversizedPayload() throws IOException {
        byte[] large = createNoisyPng(1800, 1800);
        assertTrue(large.length > UploadCardDocumentsWorker.MAX_IMAGE_SIZE_BYTES);

        Map<String, Object> variables = Map.of(
                "frontImage", Base64.getEncoder().encodeToString(large),
                "backImage", Base64.getEncoder().encodeToString(large),
                "processInstanceId", "proc-oversize"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        worker.handle(job);

        ArgumentCaptor<byte[]> frontCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> backCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(kycUserTasks).uploadCardDocuments(frontCaptor.capture(), backCaptor.capture(), eq("proc-oversize"));
        assertTrue(frontCaptor.getValue().length <= UploadCardDocumentsWorker.MAX_IMAGE_SIZE_BYTES);
        assertTrue(backCaptor.getValue().length <= UploadCardDocumentsWorker.MAX_IMAGE_SIZE_BYTES);
        assertTrue(frontCaptor.getValue().length < large.length);
        assertTrue(backCaptor.getValue().length < large.length);
    }

    private byte[] createNoisyPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(123);
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

    @Test
    void handlePropagatesUploadFailures() {
        byte[] frontBytes = "front".getBytes();
        byte[] backBytes = "back".getBytes();
        Map<String, Object> variables = Map.of(
                "frontImage", frontBytes,
                "backImage", backBytes,
                "processInstanceId", "proc-3"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadCardDocuments(frontBytes, backBytes, "proc-3");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.handle(job));
        assertEquals("Failed to upload card documents", exception.getMessage());
    }
}
