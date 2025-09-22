package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ENGLISH_INFO_FAILED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProvideEnglishPersonalInfoWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final ProvideEnglishPersonalInfoWorker worker = new ProvideEnglishPersonalInfoWorker(kycUserTasks);

    @Test
    void handleDelegatesToService() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-1",
                "firstNameEn", "John",
                "lastNameEn", "Doe",
                "email", "john.doe@example.com",
                "telephone", "0912"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).provideEnglishPersonalInfo("John", "Doe", "john.doe@example.com", "0912", "proc-1");
        assertEquals(true, result.get("englishPersonalInfoProvided"));
    }

    @Test
    void handleRejectsMissingValues() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", " ",
                "firstNameEn", "John",
                "lastNameEn", "Doe",
                "email", "john.doe@example.com",
                "telephone", "0912"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handlePropagatesServiceFailures() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-3",
                "firstNameEn", "John",
                "lastNameEn", "Doe",
                "email", "john.doe@example.com",
                "telephone", "0912"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        doThrow(new RuntimeException("boom"))
                .when(kycUserTasks).provideEnglishPersonalInfo("John", "Doe", "john.doe@example.com", "0912", "proc-3");

        WorkflowTaskException exception = assertThrows(WorkflowTaskException.class, () -> worker.handle(job));
        assertEquals(WORKFLOW_ENGLISH_INFO_FAILED, exception.getMessage());
    }
}
