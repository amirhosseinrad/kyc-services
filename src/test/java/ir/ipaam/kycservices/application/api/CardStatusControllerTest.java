package ir.ipaam.kycservices.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import ir.ipaam.kycservices.application.api.controller.CardStatusController;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
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
    private ZeebeClient zeebeClient;

    @Test
    void updateCardStatusPersistsCustomerAndUpdatesWorkflow() throws Exception {
        Customer customer = new Customer();
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCamundaInstanceId("123456");
        processInstance.setCustomer(customer);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("123456"))
                .thenReturn(Optional.of(processInstance));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SetVariablesCommandStep1 step1 = mock(SetVariablesCommandStep1.class);
        SetVariablesResponse response = mock(SetVariablesResponse.class);
        when(zeebeClient.newSetVariablesCommand(123456L)).thenReturn(step1);
        when(step1.variables(any(Map.class))).thenReturn(step1);
        when(step1.send()).thenReturn(CompletableFuture.completedFuture(response));

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

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(step1).variables(captor.capture());
        assertThat(captor.getValue()).containsEntry("card", true);
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
    }
}
