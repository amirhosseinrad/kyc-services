package ir.ipaam.kycservices.application.service;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.CardOcrClient;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardDocumentServiceTest {

    @Mock
    private CommandGateway commandGateway;

    @Mock
    private KycProcessInstanceRepository kycProcessInstanceRepository;

    @Mock
    private KycStepStatusRepository kycStepStatusRepository;

    @Mock
    private ZeebeClient zeebeClient;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CardOcrClient cardOcrClient;

    @InjectMocks
    private CardDocumentService cardDocumentService;

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

        Customer customer = new Customer();
        customer.setHasNewNationalCard(true);
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setCustomer(customer);
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "CARD_DOCUMENTS_UPLOADED"))
                .thenReturn(false);
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class))).thenReturn(null);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(cardOcrClient.extractFront(any(), any()))
                .thenReturn(new CardOcrFrontData(
                        "front-track",
                        "0452711746",
                        "امیرحسین",
                        "جوادی راد",
                        "1360-01-02",
                        "مسعود",
                        "1404-03-22",
                        0
                ));
        when(cardOcrClient.extractBack(any(), any()))
                .thenReturn(new CardOcrBackData(
                        "back-track",
                        "9G49488906",
                        "0452711746",
                        true,
                        0
                ));

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("card-documents-uploaded")).thenReturn(step2);
        when(step2.correlationKey("process-123")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenAnswer(i -> CompletableFuture.completedFuture(response));

        CardDocumentUploadResult result = cardDocumentService.uploadCardDocuments(front, back, "process-123");

        assertThat(result.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(result.body()).containsEntry("processInstanceId", "process-123")
                .containsEntry("status", "CARD_DOCUMENTS_RECEIVED")
                .containsEntry("frontImageSize", (int) front.getSize())
                .containsEntry("backImageSize", (int) back.getSize());

        ArgumentCaptor<UploadCardDocumentsCommand> captor = ArgumentCaptor.forClass(UploadCardDocumentsCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadCardDocumentsCommand command = captor.getValue();
        assertThat(command.getProcessInstanceId()).isEqualTo("process-123");
        assertThat(command.getFrontDescriptor().filename()).isEqualTo("frontImage_process-123");
        assertThat(command.getBackDescriptor().filename()).isEqualTo("backImage_process-123");
        assertThat(command.getFrontDescriptor().data()).isEqualTo(front.getBytes());
        assertThat(command.getBackDescriptor().data()).isEqualTo(back.getBytes());

        verify(cardOcrClient).extractFront(any(), eq("front.png"));
        verify(cardOcrClient).extractBack(any(), eq("back.png"));
        verify(customerRepository).save(customer);
        assertThat(customer.getFirstName()).isEqualTo("امیرحسین");
        assertThat(customer.getLastName()).isEqualTo("جوادی راد");
        assertThat(customer.getFatherName()).isEqualTo("مسعود");
        assertThat(customer.getCardSerialNumber()).isEqualTo("9G49488906");
        assertThat(customer.getCardBarcode()).isEqualTo("0452711746");
        assertThat(customer.getCardOcrFrontTrackId()).isEqualTo("front-track");
        assertThat(customer.getCardOcrBackTrackId()).isEqualTo("back-track");
        assertThat(customer.getHasNewNationalCard()).isTrue();

        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(step1).messageName("card-documents-uploaded");
        verify(step2).correlationKey("process-123");
        verify(step3).variables(variablesCaptor.capture());
        assertThat(variablesCaptor.getValue())
                .containsEntry("processInstanceId", "process-123")
                .containsEntry("kycStatus", "CARD_DOCUMENTS_UPLOADED")
                .containsEntry("card", true);
    }

    @Test
    void duplicateUploadReturnsConflict() {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                "process-123", "CARD_DOCUMENTS_UPLOADED"))
                .thenReturn(true);

        CardDocumentUploadResult result = cardDocumentService.uploadCardDocuments(front, back, "process-123");

        assertThat(result.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.body()).containsEntry("status", "CARD_DOCUMENTS_ALREADY_UPLOADED");
        verify(commandGateway, never()).sendAndWait(any());
        verifyNoInteractions(zeebeClient);
        verifyNoInteractions(cardOcrClient, customerRepository);
    }

    @Test
    void blankProcessIdThrowsIllegalArgumentException() {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        assertThatThrownBy(() -> cardDocumentService.uploadCardDocuments(front, back, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processInstanceId must be provided");
        verify(commandGateway, never()).sendAndWait(any());
        verifyNoInteractions(cardOcrClient, customerRepository);
    }

    @Test
    void oversizedFileThrowsIllegalArgumentException() {
        byte[] large = new byte[(int) CardDocumentService.MAX_IMAGE_SIZE_BYTES + 1];
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, large);
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));

        assertThatThrownBy(() -> cardDocumentService.uploadCardDocuments(front, back, "process-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("frontImage exceeds maximum size");
        verify(commandGateway, never()).sendAndWait(any());
        verifyNoInteractions(cardOcrClient, customerRepository);
    }

    @Test
    void oversizedImagesAreCompressedBeforeDispatch() throws Exception {
        byte[] large = createNoisyPng(1800, 1800);
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, large);
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, large);

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-456"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class))).thenReturn(null);

        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class);
        PublishMessageResponse response = mock(PublishMessageResponse.class);
        when(zeebeClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName("card-documents-uploaded")).thenReturn(step2);
        when(step2.correlationKey("process-456")).thenReturn(step3);
        when(step3.variables(any(Map.class))).thenReturn(step3);
        when(step3.send()).thenAnswer(i -> CompletableFuture.completedFuture(response));

        CardDocumentUploadResult result = cardDocumentService.uploadCardDocuments(front, back, "process-456");

        assertThat(result.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat((Integer) result.body().get("frontImageSize"))
                .isLessThanOrEqualTo((int) CardDocumentService.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);
        assertThat((Integer) result.body().get("backImageSize"))
                .isLessThanOrEqualTo((int) CardDocumentService.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);

        ArgumentCaptor<UploadCardDocumentsCommand> captor = ArgumentCaptor.forClass(UploadCardDocumentsCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        UploadCardDocumentsCommand command = captor.getValue();
        assertThat(command.getFrontDescriptor().data().length)
                .isLessThanOrEqualTo((int) CardDocumentService.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);
        assertThat(command.getBackDescriptor().data().length)
                .isLessThanOrEqualTo((int) CardDocumentService.MAX_IMAGE_SIZE_BYTES)
                .isLessThan(large.length);
    }

    @Test
    void commandFailurePropagates() {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));
        when(commandGateway.sendAndWait(any(UploadCardDocumentsCommand.class)))
                .thenThrow(new RuntimeException("gateway failure"));

        assertThatThrownBy(() -> cardDocumentService.uploadCardDocuments(front, back, "process-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("gateway failure");
    }

    @Test
    void missingProcessInstanceThrowsResourceNotFound() {
        MockMultipartFile front = new MockMultipartFile("frontImage", "front.png", MediaType.IMAGE_PNG_VALUE, "front".getBytes());
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardDocumentService.uploadCardDocuments(front, back, "process-123"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Process instance not found");
        verify(commandGateway, never()).sendAndWait(any());
        verifyNoInteractions(zeebeClient);
        verifyNoInteractions(cardOcrClient, customerRepository);
    }

    @Test
    void fileReadFailureThrowsFileProcessingException() throws Exception {
        MultipartFileStub multipartFile = new MultipartFileStub("frontImage", new IOException("boom"));
        MockMultipartFile back = new MockMultipartFile("backImage", "back.png", MediaType.IMAGE_PNG_VALUE, "back".getBytes());

        ProcessInstance processInstance = new ProcessInstance();
        when(kycProcessInstanceRepository.findByCamundaInstanceId("process-123"))
                .thenReturn(Optional.of(processInstance));

        assertThatThrownBy(() -> cardDocumentService.uploadCardDocuments(multipartFile, back, "process-123"))
                .isInstanceOf(FileProcessingException.class)
                .hasMessage(FILE_READ_FAILURE);
        verifyNoInteractions(cardOcrClient, customerRepository);
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

    private static final class MultipartFileStub extends MockMultipartFile {

        private final IOException exception;

        private MultipartFileStub(String name, IOException exception) throws IOException {
            super(name, name, MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1});
            this.exception = exception;
        }

        @Override
        public byte[] getBytes() throws IOException {
            throw exception;
        }
    }
}
