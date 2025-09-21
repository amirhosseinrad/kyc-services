package ir.ipaam.kycservices.application.api;

import ir.ipaam.kycservices.application.api.controller.DocumentQueryController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.common.ErrorMessageKeys;
import ir.ipaam.kycservices.infrastructure.service.DocumentNotFoundException;
import ir.ipaam.kycservices.infrastructure.service.DocumentRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentQueryController.class)
class DocumentQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentRetrievalService documentRetrievalService;

    @Test
    void fetchLatestDocumentReturnsBinaryPayload() throws Exception {
        byte[] payload = new byte[]{10, 20, 30};
        DocumentRetrievalService.RetrievedDocument retrievedDocument =
                new DocumentRetrievalService.RetrievedDocument("PHOTO", payload);

        when(documentRetrievalService.retrieveLatestDocument("0012345678", "PHOTO"))
                .thenReturn(retrievedDocument);

        mockMvc.perform(get("/kyc/documents/latest")
                        .param("nationalCode", "0012345678")
                        .param("documentType", "PHOTO"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(payload));
    }

    @Test
    void fetchLatestDocumentReturnsNotFoundWhenMissing() throws Exception {
        when(documentRetrievalService.retrieveLatestDocument(eq("0012345678"), eq("PHOTO")))
                .thenThrow(new DocumentNotFoundException(ErrorMessageKeys.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get("/kyc/documents/latest")
                        .param("nationalCode", "0012345678")
                        .param("documentType", "PHOTO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Document not found"));
    }
}
