package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.WORKFLOW_ACCEPT_CONSENT_FAILED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptConsentWorkerTest {

    private final KycUserTasks kycUserTasks = mock(KycUserTasks.class);
    private final KycServiceTasks kycServiceTasks = mock(KycServiceTasks.class);
    private final AcceptConsentWorker worker = new AcceptConsentWorker(kycUserTasks, kycServiceTasks);

    @Test
    void handleDelegatesToService() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-1",
                "termsVersion", "v1",
                "accepted", true,
                "card", true
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(1L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).acceptConsent("v1", true, "proc-1");
        assertEquals(true, result.get("consentAccepted"));
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleAcceptsStringBooleanValues() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-2",
                "termsVersion", "v2",
                "accepted", "true",
                "card", false
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(2L);

        Map<String, Object> result = worker.handle(job);

        verify(kycUserTasks).acceptConsent("v2", true, "proc-2");
        assertEquals(true, result.get("consentAccepted"));
        verifyNoInteractions(kycServiceTasks);
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
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRejectsInvalidBoolean() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-4",
                "termsVersion", "v4",
                "accepted", 123,
                "card", true
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(4L);

        assertThrows(IllegalArgumentException.class, () -> worker.handle(job));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handlePropagatesFailures() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-5",
                "termsVersion", "v5",
                "accepted", true,
                "card", true
        );
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(5L);

        doThrow(new RuntimeException("boom"))
                .when(kycUserTasks).acceptConsent("v5", true, "proc-5");

        WorkflowTaskException exception = assertThrows(WorkflowTaskException.class, () -> worker.handle(job));
        assertEquals(WORKFLOW_ACCEPT_CONSENT_FAILED, exception.getMessage());
        verify(kycServiceTasks).logFailureAndRetry(AcceptConsentWorker.STEP_NAME, WORKFLOW_ACCEPT_CONSENT_FAILED, "proc-5");
    }

    @Test
    void handleRetriesWhenConsentVariablesMissing() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-6"
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(6L);

        AcceptConsentWorker.MissingConsentVariablesException exception =
                assertThrows(AcceptConsentWorker.MissingConsentVariablesException.class, () -> worker.handle(job));
        assertTrue(exception.getMessage().contains("proc-6"));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }

    @Test
    void handleRetriesWhenCardVariableMissing() {
        Map<String, Object> variables = Map.of(
                "processInstanceId", "proc-7",
                "termsVersion", "v7",
                "accepted", true
        );

        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(variables);
        when(job.getKey()).thenReturn(7L);

        AcceptConsentWorker.MissingConsentVariablesException exception =
                assertThrows(AcceptConsentWorker.MissingConsentVariablesException.class, () -> worker.handle(job));
        assertTrue(exception.getMessage().contains("proc-7"));
        verifyNoInteractions(kycUserTasks);
        verifyNoInteractions(kycServiceTasks);
    }
}
