package ir.ipaam.kycservices.application.api;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.controller.SelfieController;
import ir.ipaam.kycservices.application.api.error.ErrorCode;
import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.INQUIRY_TOKEN_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SelfieController.class)
class SelfieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommandGateway commandGateway;

    @MockitoBean
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @MockitoBean
    private KycStepStatusRepository kycStepStatusRepository;

    @MockitoBean
    private InquiryTokenService inquiryTokenService;

    @MockitoBean
    private ZeebeClient zeebeClient;

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

        Customer customer = new Customer();
        customer.setHasNewNationalCard(true);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCustomer(customer);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "SELFIE_UPLOADED"))
                .thenReturn(false);
        when(inquiryTokenService.generateToken("process-123")).thenReturn(Optional.of("token-123"));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("selfie-uploaded")).thenReturn(step2);
        when(step2.correlationKey("process-123")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenAnswer(i -> CompletableFuture.completedFuture(response));

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

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(step1).messageName("selfie-uploaded");
        verify(step2).correlationKey("process-123");
        verify(step3).variables(variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
                .containsEntry("processInstanceId", "process-123")
                .containsEntry("kycStatus", "SELFIE_UPLOADED")
                .containsEntry("card", true);
    }

    @Test
    void duplicateSelfieReturnsConflict() throws Exception {
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

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "SELFIE_UPLOADED"))
                .thenReturn(true);

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("SELFIE_ALREADY_UPLOADED"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verifyNoInteractions(zeebeClient);
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
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("processInstanceId must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verifyNoInteractions(zeebeClient);
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
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("selfie must be provided"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verifyNoInteractions(zeebeClient);
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

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(inquiryTokenService.generateToken("process-123")).thenReturn(Optional.of("token-123"));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class)))
                .thenThrow(new CommandExecutionException("failed", new IllegalArgumentException("invalid"), null));

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.getValue()))
                .andExpect(jsonPath("$.message.en").value("invalid"));
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

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(inquiryTokenService.generateToken("process-123")).thenReturn(Optional.of("token-123"));
        when(commandGateway.sendAndWait(any(UploadSelfieCommand.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED_ERROR.getValue()))
                .andExpect(jsonPath("$.message.en").value("boom"));
    }

    @Test
    void tokenGenerationFailureMarksStepAndReturnsServiceUnavailable() throws Exception {
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

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "SELFIE_UPLOADED"))
                .thenReturn(false);
        when(inquiryTokenService.generateToken("process-123")).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("SELFIE_PENDING"))
                .andExpect(jsonPath("$.message").value("Selfie verification is temporarily unavailable. Please try again later."));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verify(kycProcessInstanceRepository).save(processInstance);

        assertThat(processInstance.getStatuses())
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.getStepName()).isEqualTo("SELFIE_UPLOADED");
                    assertThat(status.getState()).isEqualTo(StepStatus.State.FAILED);
                    assertThat(status.getErrorCause()).isEqualTo(INQUIRY_TOKEN_FAILED);
                });
    }

    @Test
    void tokenGenerationExceptionMarksStepAndReturnsServiceUnavailable() throws Exception {
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

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "SELFIE_UPLOADED"))
                .thenReturn(false);
        when(inquiryTokenService.generateToken("process-123"))
                .thenThrow(new InquiryTokenException(INQUIRY_TOKEN_FAILED));

        mockMvc.perform(multipart("/kyc/selfie")
                        .file(selfie)
                        .file(process))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.processInstanceId").value("process-123"))
                .andExpect(jsonPath("$.status").value("SELFIE_PENDING"))
                .andExpect(jsonPath("$.message").value("Selfie verification is temporarily unavailable. Please try again later."));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verify(kycProcessInstanceRepository).save(processInstance);

        assertThat(processInstance.getStatuses())
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.getStepName()).isEqualTo("SELFIE_UPLOADED");
                    assertThat(status.getState()).isEqualTo(StepStatus.State.FAILED);
                    assertThat(status.getErrorCause()).isEqualTo(INQUIRY_TOKEN_FAILED);
                });
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
                .andExpect(jsonPath("$.code").value(ErrorCode.RESOURCE_NOT_FOUND.getValue()))
                .andExpect(jsonPath("$.message.en").value("Process instance not found"));

        verify(commandGateway, never()).sendAndWait(any(UploadSelfieCommand.class));
        verifyNoInteractions(zeebeClient);
    }
}
