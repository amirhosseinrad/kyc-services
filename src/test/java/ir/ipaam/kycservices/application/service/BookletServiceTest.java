package ir.ipaam.kycservices.application.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.BookletValidationClient;
import ir.ipaam.kycservices.application.service.dto.BookletValidationData;
import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGE_TOO_LARGE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGES_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookletServiceTest {

    @Mock
    private CommandGateway commandGateway;

    @Mock
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Mock
    private KycStepStatusRepository kycStepStatusRepository;

    @Mock
    private ZeebeClient zeebeClient;

    @Mock
    private PublishMessageCommandStep1 publishMessageStep1;

    @Mock
    private PublishMessageCommandStep2 publishMessageStep2;

    @Mock
    private PublishMessageCommandStep3 publishMessageStep3;

    @Mock
    private PublishMessageResponse publishMessageResponse;

    @Mock
    private BookletValidationClient bookletValidationClient;

    private BookletService bookletService;

    @BeforeEach
    void setUp() {
        bookletService = new BookletService(
                commandGateway,
                kycProcessInstanceRepository,
                kycStepStatusRepository,
                zeebeClient,
                bookletValidationClient
        );
    }

    @Test
    void uploadBookletPagesDispatchesCommandAndPublishesMessage() {
        MockMultipartFile page1 = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile page2 = new MockMultipartFile(
                "pages",
                "page2.png",
                "image/png",
                "page2".getBytes(StandardCharsets.UTF_8)
        );

        Customer customer = new Customer();
        customer.setHasNewNationalCard(false);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCustomer(customer);

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123",
                "ID_PAGES_UPLOADED"))
                .thenReturn(false);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(publishMessageStep1);
        when(publishMessageStep1.messageName("id-pages-uploaded")).thenReturn(publishMessageStep2);
        when(publishMessageStep2.correlationKey("process-123")).thenReturn(publishMessageStep3);
        when(publishMessageStep3.variables(any(Map.class))).thenReturn(publishMessageStep3);
        when(publishMessageStep3.send()).thenReturn(CompletableFuture.completedFuture(publishMessageResponse));
        when(bookletValidationClient.validate(any(byte[].class), eq("page1.png")))
                .thenReturn(new BookletValidationData("track-1", "smart", 0));
        when(bookletValidationClient.validate(any(byte[].class), eq("page2.png")))
                .thenReturn(new BookletValidationData("track-2", "smart", 0));

        ResponseEntity<Map<String, Object>> response = bookletService.uploadBookletPages(
                List.of(page1, page2),
                "process-123"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsEntry("processInstanceId", "process-123");
        assertThat(response.getBody()).containsEntry("pageCount", 2);
        assertThat((List<?>) response.getBody().get("pageSizes")).containsExactly(
                page1.getBytes().length,
                page2.getBytes().length
        );
        assertThat(response.getBody()).containsEntry("status", "ID_PAGES_RECEIVED");
        List<?> validationResults = (List<?>) response.getBody().get("validationResults");
        assertThat(validationResults).hasSize(2);
        assertThat((Map<?, ?>) validationResults.get(0)).containsEntry("trackId", "track-1");
        assertThat((Map<?, ?>) validationResults.get(1)).containsEntry("trackId", "track-2");

        ArgumentCaptor<UploadIdPagesCommand> commandCaptor = ArgumentCaptor.forClass(UploadIdPagesCommand.class);
        verify(commandGateway).sendAndWait(commandCaptor.capture());
        UploadIdPagesCommand command = commandCaptor.getValue();
        assertThat(command.processInstanceId()).isEqualTo("process-123");
        assertThat(command.pageDescriptors()).hasSize(2);
        assertThat(command.pageDescriptors().get(0).filename()).isEqualTo("page1.png");
        assertThat(command.pageDescriptors().get(0).data()).isEqualTo(page1.getBytes());
        assertThat(command.pageDescriptors().get(1).filename()).isEqualTo("page2.png");
        assertThat(command.pageDescriptors().get(1).data()).isEqualTo(page2.getBytes());

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(publishMessageStep3).variables(variablesCaptor.capture());
        Map<String, Object> variables = variablesCaptor.getValue();
        assertThat(variables)
                .containsEntry("processInstanceId", "process-123")
                .containsEntry("kycStatus", "ID_PAGES_UPLOADED")
                .containsEntry("card", false);

        verify(bookletValidationClient).validate(any(byte[].class), eq("page1.png"));
        verify(bookletValidationClient).validate(any(byte[].class), eq("page2.png"));
    }

    @Test
    void duplicateUploadReturnsConflict() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123",
                "ID_PAGES_UPLOADED"))
                .thenReturn(true);

        ResponseEntity<Map<String, Object>> response = bookletService.uploadBookletPages(
                List.of(page),
                "process-123"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", "ID_PAGES_ALREADY_UPLOADED");

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
        verifyNoInteractions(zeebeClient);
        verifyNoInteractions(bookletValidationClient);
    }

    @Test
    void blankProcessIdThrowsIllegalArgumentException() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PROCESS_INSTANCE_ID_REQUIRED);
    }

    @Test
    void oversizedPageThrowsIllegalArgumentException() {
        byte[] large = new byte[(int) BookletService.MAX_PAGE_SIZE_BYTES + 1];
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                large
        );

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "process-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ID_PAGE_TOO_LARGE);
    }

    @Test
    void missingProcessThrowsResourceNotFoundException() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "process-123"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void commandValidationFailurePropagates() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(bookletValidationClient.validate(any(byte[].class), eq("page1.png")))
                .thenReturn(new BookletValidationData("track-1", "smart", 0));
        when(commandGateway.sendAndWait(any(UploadIdPagesCommand.class)))
                .thenThrow(new CommandExecutionException("rejected", new IllegalArgumentException("bad input")));

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "process-123"))
                .isInstanceOf(CommandExecutionException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    void commandFailurePropagates() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(bookletValidationClient.validate(any(byte[].class), eq("page1.png")))
                .thenReturn(new BookletValidationData("track-1", "smart", 0));
        when(commandGateway.sendAndWait(any(UploadIdPagesCommand.class)))
                .thenThrow(new RuntimeException("gateway failure"));

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "process-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("gateway failure");
    }

    @Test
    void validationFailurePreventsUpload() {
        MockMultipartFile page = new MockMultipartFile(
                "pages",
                "page1.png",
                "image/png",
                "page1".getBytes(StandardCharsets.UTF_8)
        );

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(bookletValidationClient.validate(any(byte[].class), eq("page1.png")))
                .thenThrow(new IllegalArgumentException("error.workflow.bookletValidation.failed"));

        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(page), "process-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.workflow.bookletValidation.failed");

        verify(commandGateway, never()).sendAndWait(any(UploadIdPagesCommand.class));
        verifyNoInteractions(zeebeClient);
    }

    @Test
    void missingPagesThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> bookletService.uploadBookletPages(List.of(), "process-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ID_PAGES_REQUIRED);
    }
}
