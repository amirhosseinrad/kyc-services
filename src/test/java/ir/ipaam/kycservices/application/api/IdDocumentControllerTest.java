package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.IdDocumentController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
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

@WebMvcTest(IdDocumentController.class)
class IdDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Test
    void uploadIdPagesDispatchesCommand() throws Exception {
        MockMultipartFile page1 = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile page2 = new MockMultipartFile(
                "pages",
                "page2.png",
                MediaType.IMAGE_PNG_VALUE,
                "page2".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page1)
                        .file(page2)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.pageSizes[0]").value(page1.getBytes().length))
                .andExpect(jsonPath("$.pageSizes[1]").value(page2.getBytes().length))
                .andExpect(jsonPath("$.status").value("ID_PAGES_RECEIVED"));

        ArgumentCaptor<UploadIdPagesCommand> captor = ArgumentCaptor.forClass(UploadIdPagesCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadIdPagesCommand command = captor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-123");
        assertThat(command.pageDescriptors()).hasSize(2);
        assertThat(command.pageDescriptors().get(0).filename()).isEqualTo("page1.png");
        assertThat(command.pageDescriptors().get(1).filename()).isEqualTo("page2.png");
        assertThat(command.pageDescriptors().get(0).data()).isEqualTo(page1.getBytes());
        assertThat(command.pageDescriptors().get(1).data()).isEqualTo(page2.getBytes());
    }

    @Test
    void blankProcessIdReturnsBadRequest() throws Exception {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
    }

    @Test
    void oversizedPageReturnsBadRequest() throws Exception {
        byte[] large = new byte[(int) IdDocumentController.MAX_PAGE_SIZE_BYTES + 1];
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                large
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("ID page exceeds maximum size"));

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
    }

    @Test
    void commandValidationFailureReturnsBadRequest() throws Exception {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadIdPagesCommand.class)))
                .thenThrow(new CommandExecutionException("rejected", new IllegalArgumentException("bad input")));

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("bad input"));
    }

    @Test
    void commandFailureReturnsServerError() throws Exception {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                MediaType.IMAGE_PNG_VALUE,
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(new ProcessInstance()));
        when(commandGateway.sendAndWait(any(UploadIdPagesCommand.class)))
                .thenThrow(new RuntimeException("gateway failure"));

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(page)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("gateway failure"));
    }

    @Test
    void missingPagesReturnsBadRequest() throws Exception {
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/documents/id")
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("At least one ID page must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
    }
}
