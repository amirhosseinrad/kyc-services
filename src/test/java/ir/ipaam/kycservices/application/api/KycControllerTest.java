package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.KycController;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.KycStepStatus;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycController.class)
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KycServiceTasks tasks;

    @Test
    void statusEndpointReturnsStatus() throws Exception {
        Customer customer = new Customer();
        customer.setNationalCode("0024683416");
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setBirthDate(LocalDate.of(1990, 1, 1));
        customer.setMobile("09123456789");
        customer.setEmail("john@example.com");

        KycProcessInstance instance = new KycProcessInstance();
        instance.setCamundaInstanceId("proc1");
        instance.setStatus("APPROVED");
        instance.setStartedAt(LocalDateTime.of(2023, 1, 1, 0, 0));
        instance.setCompletedAt(LocalDateTime.of(2023, 1, 2, 0, 0));
        instance.setCustomer(customer);

        KycStepStatus stepStatus = new KycStepStatus();
        stepStatus.setStepName("OCR");
        stepStatus.setState(KycStepStatus.State.PASSED);
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
                .andExpect(jsonPath("$.stepHistory").isArray())
                .andExpect(jsonPath("$.stepHistory[0].stepName").value("OCR"))
                .andExpect(jsonPath("$.stepHistory[0].state").value("PASSED"));
    }

    @Test
    void missingNationalCodeReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceThrowsIllegalArgumentReturnsBadRequest() throws Exception {
        when(tasks.checkKycStatus("bad"))
                .thenThrow(new IllegalArgumentException("bad code"));

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad code"));
    }

    @Test
    void serviceThrowsRuntimeExceptionReturnsServerError() throws Exception {
        when(tasks.checkKycStatus("0024683416"))
                .thenThrow(new RuntimeException("failure"));

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("failure"));
    }

    @Test
    void updateStatusReturnsNoContent() throws Exception {
        mockMvc.perform(put("/kyc/status/proc1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"stepName\":\"OCR\",\"state\":\"PASSED\"}"))
                .andExpect(status().isNoContent());
        verify(tasks).updateKycStatus("proc1", "APPROVED", "OCR", "PASSED");
    }

    @Test
    void missingStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/kyc/status/proc1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(tasks, never()).updateKycStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void invalidProcessInstanceIdReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/kyc/status/proc1!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\",\"stepName\":\"OCR\",\"state\":\"PASSED\"}"))
                .andExpect(status().isBadRequest());
        verify(tasks, never()).updateKycStatus(anyString(), anyString(), anyString(), anyString());
    }
}
