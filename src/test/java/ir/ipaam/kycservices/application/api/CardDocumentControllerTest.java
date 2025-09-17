package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.CardDocumentController;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
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
        assertThat(command.getFrontImageName()).isEqualTo("front.png");
        assertThat(command.getBackImageName()).isEqualTo("back.png");
        assertThat(command.getFrontImageContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(command.getBackImageContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE);
        assertThat(command.getFrontImage()).isEqualTo(front.getBytes());
        assertThat(command.getBackImage()).isEqualTo(back.getBytes());
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
                .andExpect(jsonPath("$.error").value("processInstanceId must be provided"));

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
                .andExpect(jsonPath("$.error").value("frontImage exceeds maximum size of "
                        + CardDocumentController.MAX_IMAGE_SIZE_BYTES + " bytes"));

        verify(commandGateway, never()).sendAndWait(any(UploadCardDocumentsCommand.class));
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

        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class)))
                .thenThrow(new RuntimeException("gateway failure"));

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to process card documents"));
    }
}
