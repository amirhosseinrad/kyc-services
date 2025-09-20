package ir.ipaam.kycservices.application.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.ipaam.kycservices.application.api.controller.EnglishPersonalInfoController;
import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
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

@WebMvcTest(EnglishPersonalInfoController.class)
class EnglishPersonalInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Test
    void provideEnglishInfoDispatchesCommand() throws Exception {
        EnglishPersonalInfoRequest request = new EnglishPersonalInfoRequest(
                " process-1 ",
                " John ",
                " Doe ",
                " john.doe@example.com ",
                " 0912 "
        );
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(ProvideEnglishPersonalInfoCommand.class))).thenReturn(null);

        mockMvc.perform(post("/kyc/english-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-1"))
                .andExpect(jsonPath("$.firstNameEn").value("John"))
                .andExpect(jsonPath("$.lastNameEn").value("Doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.telephone").value("0912"))
                .andExpect(jsonPath("$.status").value("ENGLISH_PERSONAL_INFO_PROVIDED"));

        ArgumentCaptor<ProvideEnglishPersonalInfoCommand> captor = ArgumentCaptor.forClass(ProvideEnglishPersonalInfoCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        ProvideEnglishPersonalInfoCommand command = captor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-1");
        assertThat(command.firstNameEn()).isEqualTo("John");
        assertThat(command.lastNameEn()).isEqualTo("Doe");
        assertThat(command.email()).isEqualTo("john.doe@example.com");
        assertThat(command.telephone()).isEqualTo("0912");
    }

    @Test
    void provideEnglishInfoReturnsNotFoundWhenProcessMissing() throws Exception {
        EnglishPersonalInfoRequest request = new EnglishPersonalInfoRequest(
                "process-1",
                "John",
                "Doe",
                "john.doe@example.com",
                "0912"
        );
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/kyc/english-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(ProvideEnglishPersonalInfoCommand.class));
    }

    @Test
    void provideEnglishInfoHandlesCommandValidationErrors() throws Exception {
        EnglishPersonalInfoRequest request = new EnglishPersonalInfoRequest(
                "process-1",
                "John",
                "Doe",
                "john.doe@example.com",
                "0912"
        );
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(ProvideEnglishPersonalInfoCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(post("/kyc/english-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message").value("invalid"));
    }

    @Test
    void provideEnglishInfoHandlesUnexpectedErrors() throws Exception {
        EnglishPersonalInfoRequest request = new EnglishPersonalInfoRequest(
                "process-1",
                "John",
                "Doe",
                "john.doe@example.com",
                "0912"
        );
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(ProvideEnglishPersonalInfoCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/kyc/english-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message").value("boom"));
    }

    @Test
    void provideEnglishInfoValidatesInput() throws Exception {
        EnglishPersonalInfoRequest request = new EnglishPersonalInfoRequest(
                "process-1",
                "John",
                "Doe",
                "not-an-email",
                "0912"
        );
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.of(new ProcessInstance()));

        mockMvc.perform(post("/kyc/english-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message").value("email must be a valid email address"));

        verify(commandGateway, never()).sendAndWait(any(ProvideEnglishPersonalInfoCommand.class));
    }
}
