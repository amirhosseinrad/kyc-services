package ir.ipaam.kycservices.infrastructure.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.ipaam.kycservices.application.api.dto.KycStatusResponse;
import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.EnglishPersonalInfoProvidedEvent;
import ir.ipaam.kycservices.domain.event.IdPagesUploadedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.SignatureUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.Consent;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import ir.ipaam.kycservices.infrastructure.service.impl.InquiryTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycProcessEventHandlerTest {

    private KycProcessInstanceRepository instanceRepository;
    private CustomerRepository customerRepository;
    private KycStepStatusRepository stepStatusRepository;
    private DocumentRepository documentRepository;
    private ConsentRepository consentRepository;
    private WebClient inquiryWebClient;
    private MinioStorageService storageService;
    private KycProcessEventHandler handler;
    private InquiryTokenService inquiryTokenService;
    private AtomicReference<String> lastTokenRequestProcessId;
    private AtomicReference<String> lastSelfieToken;
    private AtomicReference<String> lastVideoToken;
    private AtomicReference<String> lastSignatureToken;
    private AtomicBoolean tokenEndpointShouldFail;
    private List<CardDocumentRequest> inquiryCardRequests;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(KycProcessInstanceRepository.class);
        customerRepository = mock(CustomerRepository.class);
        stepStatusRepository = mock(KycStepStatusRepository.class);
        documentRepository = mock(DocumentRepository.class);
        consentRepository = mock(ConsentRepository.class);
        storageService = mock(MinioStorageService.class);

        lastTokenRequestProcessId = new AtomicReference<>();
        lastSelfieToken = new AtomicReference<>();
        lastVideoToken = new AtomicReference<>();
        lastSignatureToken = new AtomicReference<>();
        tokenEndpointShouldFail = new AtomicBoolean(false);
        inquiryCardRequests = new ArrayList<>();

        ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
        ObjectMapper mapper = new ObjectMapper();

        ExchangeFunction inquiryExchangeFunction = request -> {
            String path = request.url().getPath();
            String body;
            if (path.endsWith("/GenerateTempToken")) {
                String processId = UriComponentsBuilder.fromUri(request.url())
                        .build()
                        .getQueryParams()
                        .getFirst("tempTokenValue");
                lastTokenRequestProcessId.set(processId);
                if (tokenEndpointShouldFail.get()) {
                    return Mono.error(new RuntimeException("token failure"));
                }
                body = "{\"Result\":\"token-for-" + processId + "\",\"RespnseCode\":0}";
            } else if (path.endsWith("/SendProbImageByToken")) {
                lastSelfieToken.set(extractTokenFromMultipart(readBody(request, strategies)));
                body = "{\"Result\":{\"path\":\"selfie-path\",\"hash\":\"selfie-hash\"},\"RespnseCode\":0}";
            } else if (path.endsWith("/SendProbVideoByToken")) {
                lastVideoToken.set(extractTokenFromMultipart(readBody(request, strategies)));
                body = "{\"Result\":{\"path\":\"video-path\",\"hash\":\"video-hash\"},\"RespnseCode\":0}";
            } else if (path.endsWith("/SaveSignature")) {
                String payload = readBody(request, strategies);
                try {
                    JsonNode node = mapper.readTree(payload);
                    lastSignatureToken.set(node.path("tokenValue").asText(null));
                } catch (Exception e) {
                    lastSignatureToken.set(null);
                }
                body = "{\"Result\":\"Signature inserted into DB successfully with ID: signature-id\",\"RespnseCode\":0}";
            } else if (path.endsWith("/SavePersonDocument")) {
                String payload = readBody(request, strategies);
                try {
                    JsonNode node = mapper.readTree(payload);
                    CardDocumentRequest cardRequest = new CardDocumentRequest(
                            node.path("tokenValue").asText(null),
                            node.path("documentType").asInt(),
                            node.path("fileData").path("fileName").asText(null),
                            node.path("fileData").path("content").asText(null)
                    );
                    inquiryCardRequests.add(cardRequest);
                    String documentId = cardRequest.documentType == 101 ? "inquiry-front-id" : "inquiry-back-id";
                    body = "{\"Result\":\"Document inserted into DB successfully with ID: " + documentId + "\",\"RespnseCode\":0}";
                } catch (Exception e) {
                    body = "{\"RespnseCode\":1}";
                }
            } else {
                body = "{}";
            }
            return Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(body)
                            .build()
            );
        };
        inquiryWebClient = WebClient.builder().exchangeFunction(inquiryExchangeFunction).build();

        inquiryTokenService = new InquiryTokenServiceImpl(inquiryWebClient);

        handler = new KycProcessEventHandler(instanceRepository, customerRepository, stepStatusRepository, documentRepository,
                consentRepository, inquiryWebClient, inquiryTokenService, storageService);
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

        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(instance));
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 1, 12, 0);
        KycStatusUpdatedEvent event = new KycStatusUpdatedEvent(
                "proc1",
                "123",
                "COMPLETED",
                "DOCUMENT_VERIFICATION",
                "PASSED",
                timestamp);

        handler.on(event);

        assertEquals("COMPLETED", instance.getStatus());
        verify(instanceRepository).save(instance);

        ArgumentCaptor<StepStatus> captor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(captor.capture());
        StepStatus savedStatus = captor.getValue();
        assertEquals("DOCUMENT_VERIFICATION", savedStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, savedStatus.getState());
        assertEquals(timestamp, savedStatus.getTimestamp());
        assertEquals(instance, savedStatus.getProcess());
        assertTrue(instance.getStatuses().contains(savedStatus));
    }

    @Test
    void onStatusUpdatedEventWithFailureTracksFailedStep() {
        ProcessInstance instance = new ProcessInstance();

        when(instanceRepository.findByCamundaInstanceId("proc-fail")).thenReturn(Optional.of(instance));
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime timestamp = LocalDateTime.of(2024, 2, 2, 8, 0);
        String errorCode = "error.workflow.cardUpload.failed";
        KycStatusUpdatedEvent event = new KycStatusUpdatedEvent(
                "proc-fail",
                "321",
                errorCode,
                "CARD_DOCUMENTS_UPLOADED",
                "FAILED",
                timestamp);

        handler.on(event);

        assertEquals(errorCode, instance.getStatus());
        verify(instanceRepository).save(instance);

        ArgumentCaptor<StepStatus> captor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(captor.capture());
        StepStatus savedStatus = captor.getValue();
        assertEquals("CARD_DOCUMENTS_UPLOADED", savedStatus.getStepName());
        assertEquals(StepStatus.State.FAILED, savedStatus.getState());
        assertEquals(timestamp, savedStatus.getTimestamp());
        assertEquals(instance, savedStatus.getProcess());
        assertTrue(instance.getStatuses().contains(savedStatus));
    }

    @Test
    void onEnglishPersonalInfoEventUpdatesExistingCustomer() {
        Customer customer = new Customer();
        customer.setNationalCode("123");
        ProcessInstance instance = new ProcessInstance();
        instance.setStatus("STARTED");
        instance.setCustomer(customer);

        when(instanceRepository.findByCamundaInstanceId("proc-1")).thenReturn(Optional.of(instance));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime providedAt = LocalDateTime.of(2024, 1, 2, 10, 0);
        EnglishPersonalInfoProvidedEvent event = new EnglishPersonalInfoProvidedEvent(
                "proc-1",
                "123",
                "John",
                "Doe",
                "john.doe@example.com",
                "0912",
                providedAt
        );

        handler.on(event);

        assertEquals("John", customer.getFirstName());
        assertEquals("Doe", customer.getLastName());
        assertEquals("john.doe@example.com", customer.getEmail());
        assertEquals("0912", customer.getMobile());
        verify(customerRepository).save(customer);
        verify(instanceRepository).save(instance);
        assertEquals("ENGLISH_PERSONAL_INFO_PROVIDED", instance.getStatus());
        assertEquals(providedAt, instance.getCompletedAt());
        assertEquals(customer, instance.getCustomer());
    }

    @Test
    void onEnglishPersonalInfoEventCreatesCustomerWhenMissing() {
        ProcessInstance instance = new ProcessInstance();

        when(instanceRepository.findByCamundaInstanceId("proc-2")).thenReturn(Optional.of(instance));
        when(customerRepository.findByNationalCode("456")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanceRepository.save(any(ProcessInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime providedAt = LocalDateTime.of(2024, 1, 3, 9, 30);
        EnglishPersonalInfoProvidedEvent event = new EnglishPersonalInfoProvidedEvent(
                "proc-2",
                "456",
                "Alice",
                "Smith",
                "alice.smith@example.com",
                "0987",
                providedAt
        );

        handler.on(event);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, atLeastOnce()).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertEquals("456", savedCustomer.getNationalCode());
        assertEquals("Alice", savedCustomer.getFirstName());
        assertEquals("Smith", savedCustomer.getLastName());
        assertEquals("alice.smith@example.com", savedCustomer.getEmail());
        assertEquals("0987", savedCustomer.getMobile());
        assertEquals(savedCustomer, instance.getCustomer());
        verify(instanceRepository).save(instance);
        assertEquals("ENGLISH_PERSONAL_INFO_PROVIDED", instance.getStatus());
        assertEquals(providedAt, instance.getCompletedAt());
    }

    @Test
    void englishPersonalInfoEventPersistsStatusVisibleViaQueryAndResponse() {
        Customer customer = new Customer();
        customer.setNationalCode("123");
        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId("proc-3");
        instance.setStartedAt(LocalDateTime.of(2024, 1, 1, 8, 0));
        instance.setStatus("STARTED");
        instance.setCustomer(customer);

        when(instanceRepository.findByCamundaInstanceId("proc-3")).thenReturn(Optional.of(instance));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime providedAt = LocalDateTime.of(2024, 1, 4, 14, 45);
        EnglishPersonalInfoProvidedEvent event = new EnglishPersonalInfoProvidedEvent(
                "proc-3",
                "123",
                "Jane",
                "Doe",
                "jane.doe@example.com",
                "0999",
                providedAt
        );

        handler.on(event);

        verify(customerRepository).save(customer);
        verify(instanceRepository).save(instance);
        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.of(instance));

        ProcessInstance result = handler.handle(new FindKycStatusQuery("123"));
        assertSame(instance, result);
        assertEquals("ENGLISH_PERSONAL_INFO_PROVIDED", result.getStatus());
        assertEquals(providedAt, result.getCompletedAt());

        KycStatusResponse response = KycStatusResponse.success(result);
        assertEquals("ENGLISH_PERSONAL_INFO_PROVIDED", response.status());
        assertEquals(providedAt, response.completedAt());
        assertEquals("proc-3", response.camundaInstanceId());
        assertNull(response.error());
    }

    @Test
    void onCardDocumentsUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 1, 5, 9, 0);
        CardDocumentsUploadedEvent event = new CardDocumentsUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("front".getBytes(), "front-file"),
                new DocumentPayloadDescriptor("back".getBytes(), "back-file"),
                uploadedAt
        );

        DocumentMetadata frontMetadata = new DocumentMetadata();
        frontMetadata.setPath("kyc-card-documents/proc1/card-front/front-file");
        frontMetadata.setHash("front-hash");
        frontMetadata.setBranded(true);
        DocumentMetadata backMetadata = new DocumentMetadata();
        backMetadata.setPath("kyc-card-documents/proc1/card-back/back-file");
        backMetadata.setHash("back-hash");
        backMetadata.setBranded(true);

        when(storageService.upload(event.getFrontDescriptor(), "CARD_FRONT", "proc1")).thenReturn(frontMetadata);
        when(storageService.upload(event.getBackDescriptor(), "CARD_BACK", "proc1")).thenReturn(backMetadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        List<ir.ipaam.kycservices.domain.model.entity.Document> saved = captor.getAllValues();
        assertEquals("CARD_FRONT", saved.get(0).getType());
        assertEquals("kyc-card-documents/proc1/card-front/front-file", saved.get(0).getStoragePath());
        assertEquals("front-hash", saved.get(0).getHash());
        assertNull(saved.get(0).getInquiryDocumentId());
        assertTrue(saved.get(0).isVerified());
        assertEquals(processInstance, saved.get(0).getProcess());
        assertEquals("CARD_BACK", saved.get(1).getType());
        assertEquals("kyc-card-documents/proc1/card-back/back-file", saved.get(1).getStoragePath());
        assertEquals("back-hash", saved.get(1).getHash());
        assertNull(saved.get(1).getInquiryDocumentId());
        assertTrue(saved.get(1).isVerified());
        assertEquals(processInstance, saved.get(1).getProcess());
        verify(storageService).upload(event.getFrontDescriptor(), "CARD_FRONT", "proc1");
        verify(storageService).upload(event.getBackDescriptor(), "CARD_BACK", "proc1");
        assertNull(lastTokenRequestProcessId.get());
        assertTrue(inquiryCardRequests.isEmpty());

        assertEquals("CARD_DOCUMENTS_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("CARD_DOCUMENTS_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onCardDocumentsUploadedEventSkipsInquiryWhenTokenGenerationFails() {
        tokenEndpointShouldFail.set(true);

        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        CardDocumentsUploadedEvent event = new CardDocumentsUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("front".getBytes(), "front-file"),
                new DocumentPayloadDescriptor("back".getBytes(), "back-file"),
                LocalDateTime.now()
        );

        DocumentMetadata frontMetadata = new DocumentMetadata();
        frontMetadata.setPath("kyc-card-documents/proc1/card-front/front-file");
        frontMetadata.setBranded(true);
        DocumentMetadata backMetadata = new DocumentMetadata();
        backMetadata.setPath("kyc-card-documents/proc1/card-back/back-file");
        backMetadata.setBranded(true);

        when(storageService.upload(event.getFrontDescriptor(), "CARD_FRONT", "proc1")).thenReturn(frontMetadata);
        when(storageService.upload(event.getBackDescriptor(), "CARD_BACK", "proc1")).thenReturn(backMetadata);

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        List<ir.ipaam.kycservices.domain.model.entity.Document> saved = captor.getAllValues();
        assertEquals("kyc-card-documents/proc1/card-front/front-file", saved.get(0).getStoragePath());
        assertEquals("kyc-card-documents/proc1/card-back/back-file", saved.get(1).getStoragePath());
        assertNull(saved.get(0).getInquiryDocumentId());
        assertNull(saved.get(1).getInquiryDocumentId());
        assertTrue(saved.get(0).isVerified());
        assertTrue(saved.get(1).isVerified());
        verify(storageService).upload(event.getFrontDescriptor(), "CARD_FRONT", "proc1");
        verify(storageService).upload(event.getBackDescriptor(), "CARD_BACK", "proc1");
        assertTrue(inquiryCardRequests.isEmpty());
        assertNull(lastTokenRequestProcessId.get());
    }

    @Test
    void onIdPagesUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 1, 6, 11, 15);
        IdPagesUploadedEvent event = new IdPagesUploadedEvent(
                "proc1",
                "123",
                List.of(
                        new DocumentPayloadDescriptor("page1".getBytes(), "page1-file"),
                        new DocumentPayloadDescriptor("page2".getBytes(), "page2-file")
                ),
                uploadedAt
        );

        DocumentMetadata page1Metadata = new DocumentMetadata();
        page1Metadata.setPath("kyc-id-documents/proc1/id-page-1/page1-file");
        page1Metadata.setHash("id-page-hash-1");
        page1Metadata.setBranded(true);
        DocumentMetadata page2Metadata = new DocumentMetadata();
        page2Metadata.setPath("kyc-id-documents/proc1/id-page-2/page2-file");
        page2Metadata.setHash("id-page-hash-2");
        page2Metadata.setBranded(true);

        when(storageService.upload(event.pageDescriptors().get(0), "ID_PAGE_1", "proc1")).thenReturn(page1Metadata);
        when(storageService.upload(event.pageDescriptors().get(1), "ID_PAGE_2", "proc1")).thenReturn(page2Metadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        List<ir.ipaam.kycservices.domain.model.entity.Document> saved = captor.getAllValues();
        assertEquals("ID_PAGE_1", saved.get(0).getType());
        assertEquals("kyc-id-documents/proc1/id-page-1/page1-file", saved.get(0).getStoragePath());
        assertEquals("id-page-hash-1", saved.get(0).getHash());
        assertEquals("ID_PAGE_2", saved.get(1).getType());
        assertEquals("kyc-id-documents/proc1/id-page-2/page2-file", saved.get(1).getStoragePath());
        assertEquals("id-page-hash-2", saved.get(1).getHash());
        assertEquals(processInstance, saved.get(0).getProcess());
        assertEquals(processInstance, saved.get(1).getProcess());
        assertTrue(saved.get(0).isVerified());
        assertTrue(saved.get(1).isVerified());
        assertEquals(2, inquiryCardRequests.size());
        assertEquals(201, inquiryCardRequests.get(0).documentType());
        assertEquals(202, inquiryCardRequests.get(1).documentType());
        verify(storageService).upload(event.pageDescriptors().get(0), "ID_PAGE_1", "proc1");
        verify(storageService).upload(event.pageDescriptors().get(1), "ID_PAGE_2", "proc1");

        assertEquals("ID_PAGES_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("ID_PAGES_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onIdPagesUploadedEventThrowsWhenTokenGenerationFails() {
        tokenEndpointShouldFail.set(true);

        IdPagesUploadedEvent event = new IdPagesUploadedEvent(
                "proc1",
                "123",
                List.of(
                        new DocumentPayloadDescriptor("page1".getBytes(), "page1-file"),
                        new DocumentPayloadDescriptor("page2".getBytes(), "page2-file")
                ),
                LocalDateTime.now()
        );
        assertThrows(InquiryTokenException.class, () -> handler.on(event));

        verifyNoInteractions(storageService);
        verify(documentRepository, never()).save(any());
        assertTrue(inquiryCardRequests.isEmpty());
        assertEquals("proc1", lastTokenRequestProcessId.get());
    }

    @Test
    void onSelfieUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 1, 7, 10, 20);
        SelfieUploadedEvent event = new SelfieUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("selfie".getBytes(), "selfie-file"),
                uploadedAt
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("kyc-biometric/proc1/photo/selfie-file");
        storageMetadata.setHash("minio-selfie-hash");
        storageMetadata.setBranded(true);
        when(storageService.upload(event.getDescriptor(), "PHOTO", "proc1")).thenReturn(storageMetadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("PHOTO", saved.getType());
        assertEquals("kyc-biometric/proc1/photo/selfie-file", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        assertTrue(saved.isVerified());
        verify(storageService).upload(event.getDescriptor(), "PHOTO", "proc1");
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertEquals("token-for-proc1", lastSelfieToken.get());

        assertEquals("SELFIE_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("SELFIE_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onSignatureUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 2, 1, 8, 30);
        SignatureUploadedEvent event = new SignatureUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("signature".getBytes(), "signature-file"),
                uploadedAt
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("kyc-signature-documents/proc1/signature/signature-file");
        storageMetadata.setHash("signature-hash");
        storageMetadata.setInquiryDocumentId("inquiry-signature-id");
        storageMetadata.setBranded(true);

        when(storageService.upload(event.getDescriptor(), "SIGNATURE", "proc1")).thenReturn(storageMetadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("SIGNATURE", saved.getType());
        assertEquals("kyc-signature-documents/proc1/signature/signature-file", saved.getStoragePath());
        assertEquals("signature-hash", saved.getHash());
        assertNull(saved.getInquiryDocumentId());
        assertEquals(processInstance, saved.getProcess());
        assertTrue(saved.isVerified());
        verify(storageService).upload(event.getDescriptor(), "SIGNATURE", "proc1");
        assertNull(lastTokenRequestProcessId.get());
        assertNull(lastSignatureToken.get());
        assertEquals("SIGNATURE_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("SIGNATURE_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onVideoUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 1, 8, 14, 5);
        VideoUploadedEvent event = new VideoUploadedEvent(
                "proc1",
                "123",
                "provided-token",
                new DocumentPayloadDescriptor("video".getBytes(), "video-file"),
                uploadedAt
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("kyc-biometric/proc1/video/video-file");
        storageMetadata.setHash("minio-video-hash");
        storageMetadata.setBranded(false);
        when(storageService.upload(event.getDescriptor(), "VIDEO", "proc1")).thenReturn(storageMetadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("VIDEO", saved.getType());
        assertEquals("kyc-biometric/proc1/video/video-file", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        assertFalse(saved.isVerified());
        verify(storageService).upload(event.getDescriptor(), "VIDEO", "proc1");
        assertNull(lastTokenRequestProcessId.get());
        assertEquals("provided-token", lastVideoToken.get());

        assertEquals("VIDEO_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("VIDEO_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onSelfieUploadedEventThrowsWhenTokenGenerationFails() {
        tokenEndpointShouldFail.set(true);

        SelfieUploadedEvent event = new SelfieUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("selfie".getBytes(), "selfie-file"),
                LocalDateTime.now()
        );

        assertThrows(InquiryTokenException.class, () -> handler.on(event));

        verifyNoInteractions(storageService);
        verify(documentRepository, never()).save(any());
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertNull(lastSelfieToken.get());
    }

    @Test
    void onSignatureUploadedEventDoesNotCallInquiryServices() {
        tokenEndpointShouldFail.set(true);

        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime uploadedAt = LocalDateTime.of(2024, 2, 1, 9, 45);
        SignatureUploadedEvent event = new SignatureUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("signature".getBytes(), "signature-file"),
                uploadedAt
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("kyc-signature-documents/proc1/signature/signature-file");
        storageMetadata.setHash("signature-hash");
        storageMetadata.setBranded(true);

        when(storageService.upload(event.getDescriptor(), "SIGNATURE", "proc1")).thenReturn(storageMetadata);
        when(stepStatusRepository.save(any(StepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("kyc-signature-documents/proc1/signature/signature-file", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        assertNull(saved.getInquiryDocumentId());
        assertTrue(saved.isVerified());
        verify(storageService).upload(event.getDescriptor(), "SIGNATURE", "proc1");
        assertNull(lastTokenRequestProcessId.get());
        assertNull(lastSignatureToken.get());
        assertEquals("SIGNATURE_UPLOADED", processInstance.getStatus());
        verify(instanceRepository).save(processInstance);

        ArgumentCaptor<StepStatus> statusCaptor = ArgumentCaptor.forClass(StepStatus.class);
        verify(stepStatusRepository).save(statusCaptor.capture());
        StepStatus stepStatus = statusCaptor.getValue();
        assertEquals("SIGNATURE_UPLOADED", stepStatus.getStepName());
        assertEquals(StepStatus.State.PASSED, stepStatus.getState());
        assertEquals(uploadedAt, stepStatus.getTimestamp());
        assertEquals(processInstance, stepStatus.getProcess());
        assertTrue(processInstance.getStatuses().contains(stepStatus));
    }

    @Test
    void onConsentAcceptedEventPersistsConsent() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        LocalDateTime acceptedAt = LocalDateTime.of(2024, 1, 1, 12, 0);
        ConsentAcceptedEvent event = new ConsentAcceptedEvent("proc1", "123", "v1", true, acceptedAt);

        handler.on(event);

        verify(instanceRepository).save(processInstance);
        ArgumentCaptor<Consent> captor = ArgumentCaptor.forClass(Consent.class);
        verify(consentRepository).save(captor.capture());
        Consent consent = captor.getValue();
        assertEquals(processInstance, consent.getProcess());
        assertTrue(consent.isAccepted());
        assertEquals("v1", consent.getTermsVersion());
        assertEquals(acceptedAt, consent.getAcceptedAt());
        assertEquals("CONSENT_ACCEPTED", processInstance.getStatus());
    }

    @Test
    void queryReturnsPersistedInstance() {
        ProcessInstance instance = new ProcessInstance();
        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.of(instance));

        ProcessInstance result = handler.handle(new FindKycStatusQuery("123"));
        assertEquals(instance, result);
    }

    @Test
    void queryReturnsUnknownWhenNotFound() {
        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.empty());

        ProcessInstance result = handler.handle(new FindKycStatusQuery("123"));
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getStatus());
    }

    private String readBody(ClientRequest request, ExchangeStrategies strategies) {
        MockClientHttpRequest mockRequest = new MockClientHttpRequest(request.method(), request.url());
        BodyInserter.Context context = new BodyInserter.Context() {
            @Override
            public List<HttpMessageWriter<?>> messageWriters() {
                return strategies.messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest() {
                return Optional.empty();
            }

            @Override
            public Map<String, Object> hints() {
                return Collections.emptyMap();
            }
        };
        request.body().insert(mockRequest, context);
        return mockRequest.getBodyAsString().block();
    }

    private String extractTokenFromMultipart(String body) {
        if (body == null) {
            return null;
        }
        String marker = "name=\"tokenValue\"";
        int markerIndex = body.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        int start = body.indexOf("\r\n\r\n", markerIndex);
        int offset = 4;
        if (start < 0) {
            start = body.indexOf("\n\n", markerIndex);
            offset = 2;
        }
        if (start < 0) {
            return null;
        }
        start += offset;
        int end = body.indexOf("\r\n", start);
        if (end < 0) {
            end = body.indexOf('\n', start);
        }
        if (end < 0) {
            end = body.length();
        }
        return body.substring(start, end).trim();
    }

    private record CardDocumentRequest(String token, int documentType, String fileName, String content) {
    }
}
