package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.controller.ConsentController;
import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import ir.ipaam.kycservices.infrastructure.service.KycUserTasks;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsentWorkflowIntegrationTest {

    private CommandGateway commandGateway;
    private KycProcessInstanceRepository kycProcessInstanceRepository;
    private ZeebeClient zeebeClient;
    private ConsentController consentController;
    private KycUserTasks kycUserTasks;
    private KycServiceTasks kycServiceTasks;
    private AcceptConsentWorker worker;

    @BeforeEach
    void setUp() {
        commandGateway = mock(CommandGateway.class);
        kycProcessInstanceRepository = mock(KycProcessInstanceRepository.class);
        zeebeClient = mock(ZeebeClient.class);
        consentController = new ConsentController(commandGateway, kycProcessInstanceRepository, zeebeClient);
        kycUserTasks = mock(KycUserTasks.class);
        kycServiceTasks = mock(KycServiceTasks.class);
        worker = new AcceptConsentWorker(kycUserTasks, kycServiceTasks);
    }

    @Test
    void consentApiAndWorkerFlowRetriesUntilVariablesArrive() {
        String processInstanceId = "98765";
        ConsentRequest request = new ConsentRequest(processInstanceId, " v9 ", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId))
                .thenReturn(Optional.of(new ir.ipaam.kycservices.domain.model.entity.ProcessInstance()));
        when(commandGateway.sendAndWait(any(AcceptConsentCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        Map<String, Object> capturedVariables = new HashMap<>();
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("consent-accepted")).thenReturn(step2);
        when(step2.correlationKey(processInstanceId)).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenAnswer(invocation -> {
            capturedVariables.clear();
            capturedVariables.putAll((Map<String, Object>) invocation.getArgument(0));
            return step3;
        });
        when(step3.send()).thenReturn(CompletableFuture.completedFuture(response));

        ResponseEntity<Map<String, Object>> apiResponse = consentController.acceptConsent(request);
        assertEquals(202, apiResponse.getStatusCode().value());
        assertThat(apiResponse.getBody()).containsEntry("processInstanceId", processInstanceId);
        assertThat(capturedVariables).containsEntry("accepted", true)
                .containsEntry("termsVersion", "v9");

        ArgumentCaptor<AcceptConsentCommand> commandCaptor = ArgumentCaptor.forClass(AcceptConsentCommand.class);
        verify(commandGateway).sendAndWait(commandCaptor.capture());
        assertEquals(processInstanceId, commandCaptor.getValue().getProcessInstanceId());
        assertEquals("v9", commandCaptor.getValue().getTermsVersion());

        AtomicReference<Map<String, Object>> jobVariables = new AtomicReference<>(new HashMap<>(Map.of(
                "processInstanceId", processInstanceId
        )));
        io.camunda.zeebe.client.api.response.ActivatedJob job = mock(io.camunda.zeebe.client.api.response.ActivatedJob.class);
        when(job.getVariablesAsMap()).thenAnswer(invocation -> jobVariables.get());
        when(job.getKey()).thenReturn(42L);

        AcceptConsentWorker.MissingConsentVariablesException missingException = assertThrows(
                AcceptConsentWorker.MissingConsentVariablesException.class,
                () -> worker.handle(job)
        );
        assertThat(missingException.getMessage()).contains(processInstanceId);
        verifyNoInteractions(kycUserTasks);

        Map<String, Object> updatedVariables = new HashMap<>(capturedVariables);
        updatedVariables.put("processInstanceId", processInstanceId);
        jobVariables.set(updatedVariables);

        Map<String, Object> workerResult = worker.handle(job);
        assertThat(workerResult)
                .containsEntry("consentAccepted", true)
                .containsEntry("kycStatus", AcceptConsentWorker.STEP_NAME);
        assertThat(workerResult).doesNotContainKey("card");
        verify(kycUserTasks).acceptConsent("v9", true, processInstanceId);
        verify(step1).messageName("consent-accepted");
        verify(step2).correlationKey(processInstanceId);
        verify(step3).send();
    }
}
