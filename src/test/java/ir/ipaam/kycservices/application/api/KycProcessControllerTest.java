package ir.ipaam.kycservices.application.api;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import ir.ipaam.kycservices.application.api.controller.KycProcessController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.KYC_STATUS_QUERY_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycProcessController.class)
class KycProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KycServiceTasks tasks;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private ZeebeClient zeebeClient;

    @Test
    void startProcessResumesExistingInstance() throws Exception {
        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId("existing-id");
        instance.setStatus("SELFIE_UPLOADED");

        when(tasks.checkKycStatus("0024683416")).thenReturn(instance);

        mockMvc.perform(post("/kyc/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processInstanceId").value("existing-id"))
                .andExpect(jsonPath("$.status").value("SELFIE_UPLOADED"));

        verify(tasks).checkKycStatus("0024683416");
        verifyNoInteractions(zeebeClient);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void startProcessCreatesNewInstanceWhenCompletedOrMissing() throws Exception {
        ProcessInstance completedInstance = new ProcessInstance();
        completedInstance.setCamundaInstanceId("completed-id");
        completedInstance.setStatus("COMPLETED");

        when(tasks.checkKycStatus("0024683416")).thenReturn(completedInstance);

        CreateInstanceCommandStep1 step1 = mock(CreateInstanceCommandStep1.class);
        CreateInstanceCommandStep1.CreateInstanceCommandStep2 step2 = mock(CreateInstanceCommandStep1.CreateInstanceCommandStep2.class);
        CreateInstanceCommandStep1.CreateInstanceCommandStep3 step3 = mock(CreateInstanceCommandStep1.CreateInstanceCommandStep3.class);
        ProcessInstanceEvent event = mock(ProcessInstanceEvent.class);

        when(zeebeClient.newCreateInstanceCommand()).thenReturn(step1);
        when(step1.bpmnProcessId("kyc-process")).thenReturn(step2);
        when(step2.latestVersion()).thenReturn(step3);
        when(step3.variables(anyMap())).thenReturn(step3);
        when(step3.send()).thenReturn(CompletableFuture.completedFuture(event));
        when(event.getProcessInstanceKey()).thenReturn(9876L);
        when(commandGateway.send(any(StartKycProcessCommand.class))).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/kyc/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processInstanceId").value("9876"))
                .andExpect(jsonPath("$.status").value("STARTED"));

        ArgumentCaptor<StartKycProcessCommand> commandCaptor = ArgumentCaptor.forClass(StartKycProcessCommand.class);
        verify(commandGateway).send(commandCaptor.capture());
        StartKycProcessCommand command = commandCaptor.getValue();
        assertEquals("9876", command.processInstanceId());
        assertEquals("0024683416", command.nationalCode());
        verify(tasks).checkKycStatus("0024683416");
    }

    @Test
    void statusEndpointReturnsStatus() throws Exception {
        Customer customer = new Customer();
        customer.setNationalCode("0024683416");
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setBirthDate(LocalDate.of(1990, 1, 1));
        customer.setMobile("09123456789");
        customer.setEmail("john@example.com");
        customer.setHasNewNationalCard(Boolean.TRUE);

        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId("proc1");
        instance.setStatus("APPROVED");
        instance.setStartedAt(LocalDateTime.of(2023, 1, 1, 0, 0));
        instance.setCompletedAt(LocalDateTime.of(2023, 1, 2, 0, 0));
        instance.setCustomer(customer);

        StepStatus stepStatus = new StepStatus();
        stepStatus.setStepName("OCR");
        stepStatus.setState(StepStatus.State.PASSED);
        stepStatus.setTimestamp(LocalDateTime.of(2023, 1, 1, 1, 0));
        instance.setStatuses(List.of(stepStatus));

        when(tasks.checkKycStatus("0024683416")).thenReturn(instance);

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.camundaInstanceId").value("proc1"))
                .andExpect(jsonPath("$.customer.nationalCode").value("0024683416"))
                .andExpect(jsonPath("$.customer.hasNewNationalCard").value(true))
                .andExpect(jsonPath("$.stepHistory").isArray())
                .andExpect(jsonPath("$.stepHistory[0].stepName").value("OCR"))
                .andExpect(jsonPath("$.stepHistory[0].state").value("PASSED"));
    }

    @Test
    void missingNationalCodeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("Validation failed"))
                .andExpect(jsonPath("$.details.fieldErrors.nationalCode[0]").value("nationalCode is required"));
    }

    @Test
    void serviceThrowsIllegalArgumentReturnsBadRequest() throws Exception {
        when(tasks.checkKycStatus("bad"))
                .thenThrow(new IllegalArgumentException("bad code"));

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("bad code"));
    }

    @Test
    void serviceThrowsRuntimeExceptionReturnsServerError() throws Exception {
        when(tasks.checkKycStatus("0024683416"))
                .thenThrow(new IllegalStateException(KYC_STATUS_QUERY_FAILED));

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("Unable to query KYC status"))
                .andExpect(jsonPath("$.message.fa").value("امکان دریافت وضعیت احراز هویت وجود ندارد"));
    }

}
