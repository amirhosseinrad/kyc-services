package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.SelfieController;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SelfieController.class)
class SelfieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Test
    void uploadSelfieDispatchesCommand() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                "selfie".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "  process-123  ".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class))).thenReturn(null);

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("SELFIE_RECEIVED"))
                .andExpect(jsonPath("$.selfieSize").value(selfie.getBytes().length));

        ArgumentCaptor<UploadSelfieCommand> captor = ArgumentCaptor.forClass(UploadSelfieCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadSelfieCommand command = captor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-123");
        assertThat(command.selfieDescriptor().filename()).isEqualTo("selfie_process-123");
        assertThat(command.selfieDescriptor().data()).isEqualTo(selfie.getBytes());
    }

    @Test
    void blankProcessIdReturnsBadRequest() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                "selfie".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
    }

    @Test
    void emptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[0]
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("selfie must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
    }

    @Test
    void commandValidationErrorReturnsBadRequest() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                "selfie".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid"));
    }

    @Test
    void unexpectedErrorsReturnServerError() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                "selfie".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to process selfie"));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.png",
                MediaType.IMAGE_PNG_VALUE,
                "selfie".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
    }
}
