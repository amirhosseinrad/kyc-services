package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
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
