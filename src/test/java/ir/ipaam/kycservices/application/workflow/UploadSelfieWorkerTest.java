package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadSelfieWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final UploadSelfieWorker worker = new UploadSelfieWorker(kycUserTasks);

    @Test
    void handleDecodesBase64AndDelegates() {
        byte[] selfieBytes = "selfie".getBytes();
        String encoded = Base64.getEncoder().encodeToString(selfieBytes);

        Map<String, Object> variables = Map.of(
                "selfieImage", "  " + encoded + "  ",
                "processInstanceId", " proc-1 "
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadSelfie(selfieBytes, "proc-1");
        assertEquals(true, result.get("selfieUploaded"));
    }

    @Test
    void handleAcceptsRawByteArrays() {
        byte[] selfieBytes = "raw".getBytes();
        Map<String, Object> variables = Map.of(
                "selfieImage", selfieBytes,
                "processInstanceId", "proc-2"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadSelfie(selfieBytes, "proc-2");
        assertEquals(true, result.get("selfieUploaded"));
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-3"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handleRejectsInvalidBase64() {
        Map<String, Object> variables = Map.of(
                "selfieImage", "not-base64",
                "processInstanceId", "proc-4"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handlePropagatesFailuresFromService() {
        byte[] selfieBytes = "boom".getBytes();
        Map<String, Object> variables = Map.of(
                "selfieImage", selfieBytes,
                "processInstanceId", "proc-5"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadSelfie(selfieBytes, "proc-5");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.handle(job));
        assertEquals("Failed to upload selfie", exception.getMessage());
    }
}
