package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ID_UPLOAD_FAILED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UploadIdPagesWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final KycServiceTasks kycServiceTasks = mock(KycServiceTasks.class);
    private final UploadIdPagesWorker worker = new UploadIdPagesWorker(kycUserTasks, kycServiceTasks);

    @Test
    void handleDelegatesToService() {
        byte[] page1 = "page1".getBytes();
        byte[] page2 = "page2".getBytes();

        Map<String, Object> variables = Map.of(
                "pages", List.of(
                        Base64.getEncoder().encodeToString(page1),
                        Base64.getEncoder().encodeToString(page2)
                ),
                "processInstanceId", "proc-1"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        ArgumentCaptor<List<byte[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(kycUserTasks).uploadIdPages(captor.capture(), eq("proc-1"));
        List<byte[]> captured = captor.getValue();
        assertEquals(2, captured.size());
        assertArrayEquals(page1, captured.get(0));
        assertArrayEquals(page2, captured.get(1));
        assertTrue((Boolean) result.get("idPagesUploaded"));
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of("processInstanceId", "proc-1");
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsOversizedPayload() {
        byte[] large = new byte[(int) UploadIdPagesWorker.MAX_PAGE_SIZE_BYTES + 1];
        Map<String, Object> variables = Map.of(
                "pages", List.of(Base64.getEncoder().encodeToString(large)),
                "processInstanceId", "proc-2"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsTooManyPages() {
        List<String> pages = List.of(
                Base64.getEncoder().encodeToString("p1".getBytes()),
                Base64.getEncoder().encodeToString("p2".getBytes()),
                Base64.getEncoder().encodeToString("p3".getBytes()),
                Base64.getEncoder().encodeToString("p4".getBytes()),
                Base64.getEncoder().encodeToString("p5".getBytes())
        );
        Map<String, Object> variables = Map.of(
                "pages", pages,
                "processInstanceId", "proc-3"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handlePropagatesUploadFailures() {
        byte[] page = "page".getBytes();
        Map<String, Object> variables = Map.of(
                "pages", List.of(page),
                "processInstanceId", "proc-4"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        doThrow(new RuntimeException("boom")).when(kycUserTasks).uploadIdPages(List.of(page), "proc-4");

        WorkflowTaskException exception = assertThrows(WorkflowTaskException.class, () -> worker.handle(job));
        assertEquals(WORKFLOW_ID_UPLOAD_FAILED, exception.getMessage());
        verify(kycServiceTasks).logFailureAndRetry(
                UploadIdPagesWorker.STEP_NAME,
                WORKFLOW_ID_UPLOAD_FAILED,
                "proc-4");
    }
}
