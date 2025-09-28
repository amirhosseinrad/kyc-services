package ir.ipaam.kycservices.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.controller.CardStatusController;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardStatusController.class)
class CardStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private KycStepStatusRepository kycStepStatusRepository;

    @MockBean
    private ZeebeClient zeebeClient;

    @MockBean
    private CommandGateway commandGateway;

    @Test
    void updateCardStatusPersistsCustomerAndUpdatesWorkflow() throws Exception {
        Customer customer = new Customer();
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCamundaInstanceId("123456");
        processInstance.setCustomer(customer);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("123456"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "123456", "CARD_STATUS_RECORDED"))
                .thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandGateway.sendAndWait(any(UpdateKycStatusCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("card-status-recorded")).thenReturn(step2);
        when(step2.correlationKey("123456")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenReturn(CompletableFuture.completedFuture(response));

        CardStatusRequest request = new CardStatusRequest("123456", true);

        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("123456"))
                .andExpect(jsonPath("$.hasNewNationalCard").value(true))
                .andExpect(jsonPath("$.status").value("CARD_STATUS_RECORDED"));

        assertThat(customer.getHasNewNationalCard()).isTrue();
        verify(customerRepository).save(customer);

        ArgumentCaptor<UpdateKycStatusCommand> commandCaptor = ArgumentCaptor.forClass(UpdateKycStatusCommand.class);
        verify(commandGateway).sendAndWait(commandCaptor.capture());
        UpdateKycStatusCommand command = commandCaptor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("123456");
        assertThat(command.status()).isEqualTo("CARD_STATUS_RECORDED");
        assertThat(command.stepName()).isEqualTo("CARD_STATUS_RECORDED");
        assertThat(command.state()).isEqualTo("PASSED");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(step1).messageName("card-status-recorded");
        verify(step2).correlationKey("123456");
        verify(step3).variables(captor.capture());
        assertThat(captor.getValue())
                .containsEntry("card", true)
                .containsEntry("processInstanceId", "123456")
                .containsEntry("kycStatus", "CARD_STATUS_RECORDED");
    }

    @Test
    void duplicateStatusReturnsConflict() throws Exception {
        Customer customer = new Customer();
        customer.setHasNewNationalCard(Boolean.TRUE);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCamundaInstanceId("123456");
        processInstance.setCustomer(customer);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("123456"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "123456", "CARD_STATUS_RECORDED"))
                .thenReturn(true);

        CardStatusRequest request = new CardStatusRequest("123456", true);

        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processInstanceId").value("123456"))
                .andExpect(jsonPath("$.hasNewNationalCard").value(true))
                .andExpect(jsonPath("$.status").value("CARD_STATUS_ALREADY_RECORDED"));

        verifyNoInteractions(commandGateway);
        verifyNoInteractions(zeebeClient);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        when(kycProcessInstanceRepository.findByCamundaInstanceId("123"))
                .thenReturn(Optional.empty());

        CardStatusRequest request = new CardStatusRequest("123", true);

        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(customerRepository, never()).save(any(Customer.class));
        verifyNoInteractions(zeebeClient);
        verifyNoInteractions(commandGateway);
    }

    @Test
    void missingProcessInstanceIdFailsValidation() throws Exception {
        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hasNewNationalCard\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("Validation failed"))
                .andExpect(jsonPath("$.details.fieldErrors.processInstanceId[0]")
                        .value("processInstanceId is required"));
        verifyNoInteractions(commandGateway);
    }

    @Test
    void missingCardFlagFailsValidation() throws Exception {
        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processInstanceId\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("Validation failed"))
                .andExpect(jsonPath("$.details.fieldErrors.hasNewNationalCard[0]")
                        .value("hasNewNationalCard is required"));
        verifyNoInteractions(commandGateway);
    }

    @Test
    void nonNumericProcessInstanceIdReturnsBadRequest() throws Exception {
        Customer customer = new Customer();
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCamundaInstanceId("abc");
        processInstance.setCustomer(customer);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("abc"))
                .thenReturn(Optional.of(processInstance));

        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processInstanceId\":\"abc\",\"hasNewNationalCard\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be a numeric value"));
        verifyNoInteractions(commandGateway);
    }

    @Test
    void missingCustomerReturnsNotFound() throws Exception {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCamundaInstanceId("123456");
        processInstance.setCustomer(null);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("123456"))
                .thenReturn(Optional.of(processInstance));

        mockMvc.perform(post("/kyc/card/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processInstanceId\":\"123456\",\"hasNewNationalCard\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verifyNoInteractions(zeebeClient);
        verify(customerRepository, never()).save(any());
        verifyNoInteractions(commandGateway);
    }
}
