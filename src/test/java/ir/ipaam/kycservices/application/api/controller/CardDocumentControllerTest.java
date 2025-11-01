package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.impl.CardValidationServiceImpl;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardDocumentController.class)
class CardDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CardValidationServiceImpl cardValidationServiceImpl;

    @Test
    void delegatesToServiceAndReturnsResponse() throws Exception {
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

        Map<String, Object> body = Map.of(
                "processInstanceId", "process-123",
                "frontImageSize", front.getSize(),
                "backImageSize", back.getSize(),
                "status", "CARD_DOCUMENTS_RECEIVED"
        );
        when(cardValidationServiceImpl.uploadCardDocuments(any(), any(), any()))
                .thenReturn(CardDocumentUploadResult.of(HttpStatus.ACCEPTED, body));

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("CARD_DOCUMENTS_RECEIVED"))
                .andExpect(jsonPath("$.frontImageSize").value((int) front.getSize()))
                .andExpect(jsonPath("$.backImageSize").value((int) back.getSize()));
    }

    @Test
    void conflictResponseIsPropagated() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "", MediaType.IMAGE_PNG_VALUE, "back".getBytes());
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> body = Map.of(
                "processInstanceId", "process-123",
                "status", "CARD_DOCUMENTS_ALREADY_UPLOADED"
        );
        when(cardValidationServiceImpl.uploadCardDocuments(any(), any(), any()))
                .thenReturn(CardDocumentUploadResult.of(HttpStatus.CONFLICT, body));

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("CARD_DOCUMENTS_ALREADY_UPLOADED"));
    }

    @Test
    void validationErrorsFromServiceAreHandled() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "", MediaType.IMAGE_PNG_VALUE, "back".getBytes());
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        doThrow(new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED))
                .when(cardValidationServiceImpl).uploadCardDocuments(any(), any(), any());

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));
    }

    @Test
    void missingProcessInstanceFromServiceIsHandled() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "", MediaType.IMAGE_PNG_VALUE, "back".getBytes());
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        doThrow(new ResourceNotFoundException("Process instance not found"))
                .when(cardValidationServiceImpl).uploadCardDocuments(any(), any(), any());

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));
    }

    @Test
    void unexpectedErrorsAreHandled() throws Exception {
        MockMultipartFile front = new MockMultipartFile("frontImage", "", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "", MediaType.IMAGE_PNG_VALUE, "back".getBytes());
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        doThrow(new RuntimeException("gateway failure"))
                .when(cardValidationServiceImpl).uploadCardDocuments(any(), any(), any());

        mockMvc.perform(multipart("/kyc/documents/card")
                        .file(front)
                        .file(back)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("gateway failure"));
    }
}
