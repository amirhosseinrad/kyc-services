package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.VideoController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
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

import static ir.ipaam.kycservices.common.ErrorMessageKeys.INQUIRY_TOKEN_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @MockBean
    private InquiryTokenService inquiryTokenService;

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

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(inquiryTokenService.generateToken("process-456"))
                .thenReturn(Optional.of("token-123"));
        when(commandGateway.sendAndWait(any(UploadVideoCommand.class))).thenReturn(null);

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
        assertThat(command.inquiryToken()).isEqualTo("token-123");
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
    }

    @Test
    void oversizedVideoReturnsBadRequest() throws Exception {
        byte[] large = new byte[(int) VideoController.MAX_VIDEO_SIZE_BYTES + 1];
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "video.mp4",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                large
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
                .andExpect(jsonPath("$.message.en").value("video exceeds maximum size"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
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

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(inquiryTokenService.generateToken("process-456"))
                .thenReturn(Optional.of("token-123"));
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

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(inquiryTokenService.generateToken("process-456"))
                .thenReturn(Optional.of("token-123"));
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
    }

    @Test
    void tokenGenerationFailureReturnsBadGateway() throws Exception {
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
                .thenReturn(Optional.of(new ProcessInstance()));
        when(inquiryTokenService.generateToken("process-456"))
                .thenThrow(new InquiryTokenException(INQUIRY_TOKEN_FAILED));

        mockMvc.perform(multipart("/kyc/video")
                        .file(video)
                        .file(process))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ErrorCode.INQUIRY_SERVICE_UNAVAILABLE.getValue()))
                .andExpect(jsonPath("$.message.en").value("Unable to generate inquiry token"));

        verify(commandGateway, never()).sendAndWait(any(UploadVideoCommand.class));
    }
}
