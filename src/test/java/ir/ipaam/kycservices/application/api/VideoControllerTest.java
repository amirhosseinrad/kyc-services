package ir.ipaam.kycservices.application.api;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.controller.VideoController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandGateway commandGateway;

    @MockitoBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @MockitoBean
    private KycStepStatusRepository kycStepStatusRepository;

    @MockitoBean
    private ZeebeClient zeebeClient;

    @Test
    void uploadVideoDispatchesCommand() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                " process-456 ".getBytes(StandardCharsets.UTF_8)
        );

        Customer customer = new Customer();
        customer.setHasNewNationalCard(true);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCustomer(customer);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-456", "VIDEO_UPLOADED"))
                .thenReturn(false);
        when(commandGateway.sendAndWait(any(UploadVideoCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("video-uploaded")).thenReturn(step2);
        when(step2.correlationKey("process-456")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenAnswer(i -> CompletableFuture.completedFuture(response));

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-456"))
                .andExpect(jsonPath("$.status").value("VIDEO_RECEIVED"))
                .andExpect(jsonPath("$.videoSize").value(video.getBytes().length));

        ArgumentCaptor<UploadVideoCommand> captor = ArgumentCaptor.forClass(UploadVideoCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadVideoCommand command = captor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-456");
        assertThat(command.videoDescriptor().filename()).isEqualTo("video_process-456");
        assertThat(command.videoDescriptor().data()).isEqualTo(video.getBytes());

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(step1).messageName("video-uploaded");
        verify(step2).correlationKey("process-456");
        verify(step3).variables(variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
                .containsEntry("processInstanceId", "process-456")
                .containsEntry("kycStatus", "VIDEO_UPLOADED")
                .containsEntry("card", true);
    }

    @Test
    void duplicateVideoReturnsConflict() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-456", "VIDEO_UPLOADED"))
                .thenReturn(true);

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processInstanceId").value("process-456"))
                .andExpect(jsonPath("$.status").value("VIDEO_ALREADY_UPLOADED"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void blankProcessIdReturnsBadRequest() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void emptyVideoReturnsBadRequest() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[0]
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("video must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void commandValidationErrorReturnsBadRequest() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadVideoCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("invalid"));
    }

    @Test
    void unexpectedErrorsReturnServerError() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadVideoCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("boom"));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "video-data".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
        verifyNoInteractions(zeebeClient);
    }
}
