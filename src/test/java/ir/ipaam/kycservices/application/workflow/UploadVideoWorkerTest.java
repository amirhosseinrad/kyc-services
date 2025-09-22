package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadVideoWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final KycServiceTasks kycServiceTasks = mock(KycServiceTasks.class);
    private final UploadVideoWorker worker = new UploadVideoWorker(kycUserTasks, kycServiceTasks);

    @Test
    void handleDecodesBase64AndDelegates() {
        byte[] videoBytes = "video".getBytes();
        String encoded = Base64.getEncoder().encodeToString(videoBytes);

        Map<String, Object> variables = Map.of(
                "videoFile", "\t" + encoded + "\n",
                "processInstanceId", " proc-1 "
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(10L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadVideo(videoBytes, "proc-1");
        assertEquals(true, result.get("videoUploaded"));
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleAcceptsRawByteArrays() {
        byte[] videoBytes = "raw-video".getBytes();
        Map<String, Object> variables = Map.of(
                "videoFile", videoBytes,
                "processInstanceId", "proc-2"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(11L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadVideo(videoBytes, "proc-2");
        assertEquals(true, result.get("videoUploaded"));
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of(
                "videoFile", Base64.getEncoder().encodeToString("video".getBytes())
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(12L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsInvalidBase64() {
        Map<String, Object> variables = Map.of(
                "videoFile", "@@bad@@",
                "processInstanceId", "proc-4"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(13L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handlePropagatesFailuresFromService() {
        byte[] videoBytes = "boom".getBytes();
        Map<String, Object> variables = Map.of(
                "videoFile", videoBytes,
                "processInstanceId", "proc-5"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(14L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadVideo(videoBytes, "proc-5");

        WorkflowTaskException exception = assertThrows(WorkflowTaskException.class, () -> worker.handle(job));
        assertEquals(WORKFLOW_VIDEO_UPLOAD_FAILED, exception.getMessage());
        verify(kycServiceTasks).logFailureAndRetry(
                UploadVideoWorker.STEP_NAME,
                WORKFLOW_VIDEO_UPLOAD_FAILED,
                "proc-5");
    }
}
