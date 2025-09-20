package ir.ipaam.kycservices.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.ipaam.kycservices.application.api.controller.ConsentController;
import ir.ipaam.kycservices.application.api.dto.ConsentRequest;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Test
    void acceptConsentDispatchesCommand() throws Exception {
        ConsentRequest request = new ConsentRequest(" process-123 ", " v1 ", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(AcceptConsentCommand.class))).thenReturn(null);

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.termsVersion").value("v1"))
                .andExpect(jsonPath("$.status").value("CONSENT_ACCEPTED"));

        ArgumentCaptor<AcceptConsentCommand> captor = ArgumentCaptor.forClass(AcceptConsentCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        AcceptConsentCommand command = captor.getValue();
        assertThat(command.getProcessInstanceId()).isEqualTo("process-123");
        assertThat(command.getTermsVersion()).isEqualTo("v1");
        assertThat(command.isAccepted()).isTrue();
    }

    @Test
    void rejectWhenAcceptedFalse() throws Exception {
        ConsentRequest request = new ConsentRequest("process-123", "v1", false);

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("accepted must be true"));

        verify(commandGateway, never()).sendAndWait(any(AcceptConsentCommand.class));
    }

    @Test
    void missingProcessInstanceIdReturnsValidationError() throws Exception {
        String payload = "{" +
                "\"termsVersion\":\"v1\"," +
                "\"accepted\":true" +
                "}";

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("Validation failed"))
                .andExpect(jsonPath("$.details.fieldErrors.processInstanceId[0]")
                        .value("processInstanceId is required"));
    }

    @Test
    void commandValidationErrorReturnsBadRequest() throws Exception {
        ConsentRequest request = new ConsentRequest("process-123", "v1", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(AcceptConsentCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("invalid"));
    }

    @Test
    void commandWithoutCauseReturnsConflict() throws Exception {
        ConsentRequest request = new ConsentRequest("process-123", "v1", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(AcceptConsentCommand.class)))
                .thenThrow(new CommandExecutionException("rejected", null));

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.COMMAND_REJECTED.getValue()))
                .andExpect(jsonPath("$.message.en").value("rejected"));
    }

    @Test
    void unexpectedErrorsReturnServerError() throws Exception {
        ConsentRequest request = new ConsentRequest("process-123", "v1", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(AcceptConsentCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("boom"));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        ConsentRequest request = new ConsentRequest("process-123", "v1", true);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/kyc/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(AcceptConsentCommand.class));
    }
}
