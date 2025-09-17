package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadSignatureWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final UploadSignatureWorker worker = new UploadSignatureWorker(kycUserTasks);

    @Test
    void handleDecodesBase64AndDelegates() {
        byte[] signatureBytes = "signature".getBytes();
        String encoded = Base64.getEncoder().encodeToString(signatureBytes);

        Map<String, Object> variables = Map.of(
                "signatureImage", "  " + encoded + "  ",
                "processInstanceId", " proc-1 "
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadSignature(signatureBytes, "proc-1");
        assertEquals(true, result.get("signatureUploaded"));
    }

    @Test
    void handleAcceptsRawByteArrays() {
        byte[] signatureBytes = "raw".getBytes();
        Map<String, Object> variables = Map.of(
                "signatureImage", signatureBytes,
                "processInstanceId", "proc-2"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).uploadSignature(signatureBytes, "proc-2");
        assertEquals(true, result.get("signatureUploaded"));
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
                "signatureImage", "not-base64",
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
        byte[] signatureBytes = "boom".getBytes();
        Map<String, Object> variables = Map.of(
                "signatureImage", signatureBytes,
                "processInstanceId", "proc-5"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadSignature(signatureBytes, "proc-5");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.handle(job));
        assertEquals("Failed to upload signature", exception.getMessage());
    }
}
