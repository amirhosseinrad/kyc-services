package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.CardDocumentController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardDocumentController.class)
class CardDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Test
    void uploadCardDocumentsDispatchesCommand() throws Exception {
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                "front".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                "back".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class))).thenReturn(null);

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("CARD_DOCUMENTS_RECEIVED"))
                .andExpect(jsonPath("$.frontImageSize").value(front.getBytes().length))
                .andExpect(jsonPath("$.backImageSize").value(back.getBytes().length));

        ArgumentCaptor<UploadCardDocumentsCommand> captor = ArgumentCaptor.forClass(UploadCardDocumentsCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadCardDocumentsCommand command = captor.getValue();
        assertThat(command.getProcessInstanceId()).isEqualTo("process-123");
        assertThat(command.getFrontDescriptor().filename()).isEqualTo("frontImage_process-123");
        assertThat(command.getBackDescriptor().filename()).isEqualTo("backImage_process-123");
        assertThat(command.getFrontDescriptor().data()).isEqualTo(front.getBytes());
        assertThat(command.getBackDescriptor().data()).isEqualTo(back.getBytes());
    }

    @Test
    void blankProcessIdReturnsBadRequest() throws Exception {
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                "front".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                "back".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadCardDocumentsCommand.class));
    }

    @Test
    void oversizedFileReturnsBadRequest() throws Exception {
        byte[] large = new byte[(int) CardDocumentController.MAX_IMAGE_SIZE_BYTES + 1];
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                large
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                "back".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("frontImage exceeds maximum size"));

        verify(commandGateway, never()).sendAndWait(any(UploadCardDocumentsCommand.class));
    }

    @Test
    void oversizedImagesAreCompressedBeforeDispatch() throws Exception {
        byte[] large = createNoisyPng(1800, 1800);
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                large
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                large
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-456".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class))).thenReturn(null);

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.frontImageSize").value(org.hamcrest.Matchers.lessThanOrEqualTo((int) CardDocumentController.MAX_IMAGE_SIZE_BYTES)))
                .andExpect(jsonPath("$.backImageSize").value(org.hamcrest.Matchers.lessThanOrEqualTo((int) CardDocumentController.MAX_IMAGE_SIZE_BYTES)));

        ArgumentCaptor<UploadCardDocumentsCommand> captor = ArgumentCaptor.forClass(UploadCardDocumentsCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadCardDocumentsCommand command = captor.getValue();
        assertThat(command.getFrontDescriptor().data().length)
                .isLessThanOrEqualTo((int) CardDocumentController.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);
        assertThat(command.getBackDescriptor().data().length)
                .isLessThanOrEqualTo((int) CardDocumentController.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);
    }

    @Test
    void commandFailureReturnsServerError() throws Exception {
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                "front".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                "back".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class)))
                .thenThrow(new RuntimeException("gateway failure"));

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("gateway failure"));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        MockMultipartFile front = new MockMultipartFile(
                "frontImage",
                "front.png",
                MediaType.IMAGE_PNG_VALUE,
                "front".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile back = new MockMultipartFile(
                "backImage",
                "back.png",
                MediaType.IMAGE_PNG_VALUE,
                "back".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadCardDocumentsCommand.class));
}

    private byte[] createNoisyPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(321);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256)).getRGB();
                image.setRGB(x, y, rgb);
            }
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        }
    }
}
