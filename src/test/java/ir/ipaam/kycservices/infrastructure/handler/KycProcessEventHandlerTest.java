package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KycProcessEventHandlerTest {

    private KycProcessInstanceRepository instanceRepository;
    private CustomerRepository customerRepository;
    private DocumentRepository documentRepository;
    private ConsentRepository consentRepository;
    private MinioStorageService storageService;
    private KycProcessEventHandler handler;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(KycProcessInstanceRepository.class);
        customerRepository = mock(CustomerRepository.class);
        documentRepository = mock(DocumentRepository.class);
        consentRepository = mock(ConsentRepository.class);
        storageService = mock(MinioStorageService.class);

        handler = new KycProcessEventHandler(
                instanceRepository,
                customerRepository,
        documentRepository,
        consentRepository,
        storageService);
    }

    @Test
    void onEventCreatesProcessInstance() {
        when(customerRepository.findByNationalCode("123")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycProcessStartedEvent event = new KycProcessStartedEvent("proc1", "123", LocalDateTime.now());
        handler.on(event);

        verify(instanceRepository).save(any(ProcessInstance.class));
    }

    @Test
    void onStatusUpdatedEventUpdatesProcessInstance() {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatuses(new ArrayList<>());

        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(instance));

        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 1, 12, 0);
        KycStatusUpdatedEvent event = new KycStatusUpdatedEvent(
                "proc1",
                "123",
                "COMPLETED",
                "DOCUMENT_VERIFICATION",
                "PASSED",
                timestamp);

        handler.on(event);

        assertThat(instance.getStatus()).isEqualTo("COMPLETED");
        verify(instanceRepository).save(instance);
    }

    @Test
    void onCardDocumentsUploadedEventStoresMetadata() {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatuses(new ArrayList<>());
        when(instanceRepository.findByCamundaInstanceId("proc-card")).thenReturn(Optional.of(instance));

        DocumentMetadata frontMetadata = new DocumentMetadata();
        frontMetadata.setPath("front-path");
        DocumentMetadata backMetadata = new DocumentMetadata();
        backMetadata.setPath("back-path");

        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("CARD_FRONT"), eq("proc-card")))
                .thenReturn(frontMetadata);
        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("CARD_BACK"), eq("proc-card")))
                .thenReturn(backMetadata);

        Document frontDocument = new Document();
        Document backDocument = new Document();
        when(documentRepository.save(any(Document.class))).thenReturn(frontDocument, backDocument);

        handler.on(new CardDocumentsUploadedEvent(
                "proc-card",
                "123",
                new DocumentPayloadDescriptor(new byte[]{1}, "front"),
                new DocumentPayloadDescriptor(new byte[]{2}, "back"),
                LocalDateTime.now()));

        verify(storageService).upload(any(DocumentPayloadDescriptor.class), eq("CARD_FRONT"), eq("proc-card"));
        verify(storageService).upload(any(DocumentPayloadDescriptor.class), eq("CARD_BACK"), eq("proc-card"));
        verify(documentRepository, times(2)).save(any(Document.class));
    }

    @Test
    void onSelfieUploadedEventPersistsDocumentAndStep() {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatuses(new ArrayList<>());
        when(instanceRepository.findByCamundaInstanceId("proc-selfie")).thenReturn(Optional.of(instance));

        DocumentMetadata selfieMetadata = new DocumentMetadata();
        selfieMetadata.setPath("selfie-path");
        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("PHOTO"), eq("proc-selfie")))
                .thenReturn(selfieMetadata);

        handler.on(new SelfieUploadedEvent(
                "proc-selfie",
                "123",
                new DocumentPayloadDescriptor(new byte[]{3}, "selfie"),
                LocalDateTime.now()));

        verify(storageService).upload(any(DocumentPayloadDescriptor.class), eq("PHOTO"), eq("proc-selfie"));
        verify(documentRepository).save(any(Document.class));
        verify(instanceRepository).save(instance);
    }

    @Test
    void onVideoUploadedEventPersistsDocumentAndStep() {
        ProcessInstance instance = new ProcessInstance();
        instance.setStatuses(new ArrayList<>());
        when(instanceRepository.findByCamundaInstanceId("proc-video")).thenReturn(Optional.of(instance));

        DocumentMetadata videoMetadata = new DocumentMetadata();
        videoMetadata.setPath("video-path");
        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("VIDEO"), eq("proc-video")))
                .thenReturn(videoMetadata);

        handler.on(new VideoUploadedEvent(
                "proc-video",
                "123",
                new DocumentPayloadDescriptor(new byte[]{4}, "video"),
                LocalDateTime.now()));

        verify(storageService).upload(any(DocumentPayloadDescriptor.class), eq("VIDEO"), eq("proc-video"));
        verify(documentRepository).save(any(Document.class));
        verify(instanceRepository).save(instance);
    }

    @Test
    void queryReturnsLatestProcessStatus() {
        ProcessInstance latest = new ProcessInstance();
        latest.setStatus("COMPLETED");

        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.of(latest));

        ProcessInstance result = handler.handle(new FindKycStatusQuery("123"));

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }
}
