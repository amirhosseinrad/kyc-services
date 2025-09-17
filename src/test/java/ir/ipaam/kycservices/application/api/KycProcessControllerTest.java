package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.KycProcessController;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
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

@WebMvcTest(KycProcessController.class)
class KycProcessControllerTest {

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

}
