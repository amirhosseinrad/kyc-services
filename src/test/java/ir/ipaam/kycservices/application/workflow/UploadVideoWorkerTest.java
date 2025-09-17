package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadVideoWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final UploadVideoWorker worker = new UploadVideoWorker(kycUserTasks);

    @Test
    void handleDelegatesToService() {
        byte[] videoBytes = "video".getBytes();

        Map<String, Object> variables = Map.of(
                "video", Base64.getEncoder().encodeToString(videoBytes),
                "processInstanceId", "proc-1"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadVideo(videoBytes, "proc-1");
        assertTrue((Boolean) result.get("videoUploaded"));
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of(
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
        byte[] large = new byte[(int) UploadVideoWorker.MAX_VIDEO_SIZE_BYTES + 1];
        Map<String, Object> variables = Map.of(
                "video", large,
                "processInstanceId", "proc-2"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handlePropagatesUploadFailures() {
        byte[] videoBytes = "video".getBytes();
        Map<String, Object> variables = Map.of(
                "video", videoBytes,
                "processInstanceId", "proc-3"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadVideo(videoBytes, "proc-3");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.handle(job));
        assertEquals("Failed to upload video", exception.getMessage());
    }
}
