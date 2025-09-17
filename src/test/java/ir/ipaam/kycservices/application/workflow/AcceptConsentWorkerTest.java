package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptConsentWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final AcceptConsentWorker worker = new AcceptConsentWorker(kycUserTasks);

    @Test
    void handleDelegatesToService() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-1",
                "termsVersion", "v1",
                "accepted", true
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).acceptConsent("v1", true, "proc-1");
        assertEquals(true, result.get("consentAccepted"));
    }

    @Test
    void handleAcceptsStringBooleanValues() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-2",
                "termsVersion", "v2",
                "accepted", "true"
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).acceptConsent("v2", true, "proc-2");
        assertEquals(true, result.get("consentAccepted"));
    }

    @Test
    void handleRejectsInvalidPayload() {
        Map<String, Object> variables = Map.of(
                "termsVersion", "v1",
                "accepted", true
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(3L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handleRejectsInvalidBoolean() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-4",
                "termsVersion", "v4",
                "accepted", 123
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
    }

    @Test
    void handlePropagatesFailures() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-5",
                "termsVersion", "v5",
                "accepted", true
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        doThrow(new RuntimeException("boom"))
                .when(kycUserTasks).acceptConsent("v5", true, "proc-5");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> worker.handle(job));
        assertEquals("Failed to accept consent", exception.getMessage());
    }
}
