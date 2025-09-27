package ir.ipaam.kycservices.application.api;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.controller.SignatureController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SignatureController.class)
class SignatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommandGateway commandGateway;

    @MockBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @MockBean
    private ZeebeClient zeebeClient;

    @Test
    void uploadSignatureDispatchesCommand() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                "signature".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "  process-123  ".getBytes(StandardCharsets.UTF_8)
        );

        Customer customer = new Customer();
        customer.setHasNewNationalCard(false);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCustomer(customer);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadSignatureCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("signature-uploaded")).thenReturn(step2);
        when(step2.correlationKey("process-123")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenReturn(CompletableFuture.completedFuture(response));

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("SIGNATURE_RECEIVED"))
                .andExpect(jsonPath("$.signatureSize").value(signature.getBytes().length));

        ArgumentCaptor<UploadSignatureCommand> captor = ArgumentCaptor.forClass(UploadSignatureCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadSignatureCommand command = captor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-123");
        assertThat(command.signatureDescriptor().filename()).isEqualTo("signature_process-123");
        assertThat(command.signatureDescriptor().data()).isEqualTo(signature.getBytes());

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(step1).messageName("signature-uploaded");
        verify(step2).correlationKey("process-123");
        verify(step3).variables(variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
                .containsEntry("processInstanceId", "process-123")
                .containsEntry("kycStatus", "SIGNATURE_UPLOADED")
                .containsEntry("card", false);
    }

    @Test
    void blankProcessIdReturnsBadRequest() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                "signature".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSignatureCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void emptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[0]
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("signature must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSignatureCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void commandValidationErrorReturnsBadRequest() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                "signature".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadSignatureCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("invalid"));
    }

    @Test
    void unexpectedErrorsReturnServerError() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                "signature".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadSignatureCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("boom"));
    }

    @Test
    void missingProcessInstanceReturnsNotFound() throws Exception {
        MockMultipartFile signature = new MockMultipartFile(
                "signature",
                "signature.png",
                MediaType.IMAGE_PNG_VALUE,
                "signature".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile process = new MockMultipartFile(
                "processInstanceId",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                "process-123".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/signature")
                        .file(signature)
                        .file(process))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadSignatureCommand.class));
        verifyNoInteractions(zeebeClient);
    }
}
