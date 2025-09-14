package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycController.class)
class KycControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KycServiceTasks tasks;

    @Test
    void statusEndpointReturnsStatus() throws Exception {
        when(tasks.checkKycStatus("0024683416")).thenReturn("APPROVED");

        mockMvc.perform(get("/kyc/status").param("nationalCode", "0024683416"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void missingNationalCodeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/kyc/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serviceThrowsIllegalArgumentReturnsBadRequest() throws Exception {
        when(tasks.checkKycStatus("bad"))
                .thenThrow(new IllegalArgumentException("bad code"));

        mockMvc.perform(get("/kyc/status").param("nationalCode", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad code"));
    }

    @Test
    void serviceThrowsRuntimeExceptionReturnsServerError() throws Exception {
        when(tasks.checkKycStatus("0024683416"))
                .thenThrow(new RuntimeException("failure"));

        mockMvc.perform(get("/kyc/status").param("nationalCode", "0024683416"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("failure"));
    }
}
