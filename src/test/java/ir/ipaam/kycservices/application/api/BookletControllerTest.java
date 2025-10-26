package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.BookletController;
import ir.ipaam.kycservices.application.service.BookletService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGES_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookletController.class)
class BookletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookletService bookletService;

    @Test
    void uploadBookletPagesDelegatesToService() throws Exception {
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

        Map<String, Object> body = Map.of(
                "processInstanceId", "process-123",
                "pageCount", 2,
                "pageSizes", List.of(page1.getBytes().length, page2.getBytes().length),
                "status", "ID_PAGES_RECEIVED"
        );
        when(bookletService.uploadBookletPages(any(), eq("process-123")))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body(body));

        mockMvc.perform(multipart("/kyc/booklets")
                        .file(page1)
                        .file(page2)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.pageSizes[0]").value(page1.getBytes().length))
                .andExpect(jsonPath("$.pageSizes[1]").value(page2.getBytes().length))
                .andExpect(jsonPath("$.status").value("ID_PAGES_RECEIVED"));

        ArgumentCaptor<List<MultipartFile>> captor = ArgumentCaptor.forClass(List.class);
        verify(bookletService).uploadBookletPages(captor.capture(), eq("process-123"));
        List<MultipartFile> captured = captor.getValue();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getOriginalFilename()).isEqualTo("page1.png");
        assertThat(captured.get(1).getOriginalFilename()).isEqualTo("page2.png");
    }

    @Test
    void serviceValidationErrorIsTranslatedToBadRequest() throws Exception {
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(bookletService.uploadBookletPages(any(), any()))
                .thenThrow(new IllegalArgumentException(ID_PAGES_REQUIRED));

        mockMvc.perform(multipart("/kyc/booklets")
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.en").value("At least one ID page must be provided"));
    }
}
