package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.KycController;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
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
        when(tasks.checkKycStatus("0024683416")).thenReturn("APPROVED");

        mockMvc.perform(post("/kyc/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\"0024683416\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
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
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNoContent());
        verify(tasks).updateKycStatus("proc1", "APPROVED");
    }

    @Test
    void missingStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/kyc/status/proc1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(tasks, never()).updateKycStatus(anyString(), anyString());
    }

    @Test
    void invalidProcessInstanceIdReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/kyc/status/proc1!")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());
        verify(tasks, never()).updateKycStatus(anyString(), anyString());
    }
}
