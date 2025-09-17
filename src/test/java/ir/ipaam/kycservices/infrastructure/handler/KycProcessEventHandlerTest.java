package ir.ipaam.kycservices.infrastructure.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.SignatureUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
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
import ir.ipaam.kycservices.infrastructure.service.BiometricStorageClient;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    private WebClient cardWebClient;
    private WebClient inquiryWebClient;
    private BiometricStorageClient biometricStorageClient;
    private KycProcessEventHandler handler;
    private AtomicReference<String> lastTokenRequestProcessId;
    private AtomicReference<String> lastSelfieToken;
    private AtomicReference<String> lastVideoToken;
    private AtomicReference<String> lastSignatureToken;
    private AtomicBoolean tokenEndpointShouldFail;

    @BeforeEach
    void setUp() {
        instanceRepository = mock(KycProcessInstanceRepository.class);
        customerRepository = mock(CustomerRepository.class);
        stepStatusRepository = mock(KycStepStatusRepository.class);
        documentRepository = mock(DocumentRepository.class);
        consentRepository = mock(ConsentRepository.class);
        biometricStorageClient = mock(BiometricStorageClient.class);

        lastTokenRequestProcessId = new AtomicReference<>();
        lastSelfieToken = new AtomicReference<>();
        lastVideoToken = new AtomicReference<>();
        lastSignatureToken = new AtomicReference<>();
        tokenEndpointShouldFail = new AtomicBoolean(false);

        ExchangeFunction cardExchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"front\":{\"path\":\"front-path\",\"hash\":\"front-hash\"}," +
                                "\"back\":{\"path\":\"back-path\",\"hash\":\"back-hash\"}}")
                        .build()
        );
        cardWebClient = WebClient.builder().exchangeFunction(cardExchangeFunction).build();

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

        handler = new KycProcessEventHandler(instanceRepository, customerRepository, stepStatusRepository, documentRepository,
                consentRepository, cardWebClient, inquiryWebClient, biometricStorageClient);
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
    void onCardDocumentsUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        CardDocumentsUploadedEvent event = new CardDocumentsUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("front".getBytes(), "front-file"),
                new DocumentPayloadDescriptor("back".getBytes(), "back-file"),
                LocalDateTime.now()
        );

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        List<ir.ipaam.kycservices.domain.model.entity.Document> saved = captor.getAllValues();
        assertEquals("CARD_FRONT", saved.get(0).getType());
        assertEquals("front-path", saved.get(0).getStoragePath());
        assertEquals(processInstance, saved.get(0).getProcess());
        assertEquals("CARD_BACK", saved.get(1).getType());
        assertEquals("back-path", saved.get(1).getStoragePath());
        assertEquals(processInstance, saved.get(1).getProcess());
    }

    @Test
    void onSelfieUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        SelfieUploadedEvent event = new SelfieUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("selfie".getBytes(), "selfie-file"),
                LocalDateTime.now()
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("minio-selfie-path");
        storageMetadata.setHash("minio-selfie-hash");
        when(biometricStorageClient.upload(any(), any(), any())).thenReturn(storageMetadata);

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("PHOTO", saved.getType());
        assertEquals("minio-selfie-path", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        verify(biometricStorageClient).upload(event.getDescriptor(), "PHOTO", "proc1");
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertEquals("token-for-proc1", lastSelfieToken.get());
    }

    @Test
    void onSignatureUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        SignatureUploadedEvent event = new SignatureUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("signature".getBytes(), "signature-file"),
                LocalDateTime.now()
        );

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("SIGNATURE", saved.getType());
        assertEquals("signature-id", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertEquals("token-for-proc1", lastSignatureToken.get());
    }

    @Test
    void onVideoUploadedEventStoresMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(processInstance));

        VideoUploadedEvent event = new VideoUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("video".getBytes(), "video-file"),
                LocalDateTime.now()
        );

        DocumentMetadata storageMetadata = new DocumentMetadata();
        storageMetadata.setPath("minio-video-path");
        storageMetadata.setHash("minio-video-hash");
        when(biometricStorageClient.upload(any(), any(), any())).thenReturn(storageMetadata);

        handler.on(event);

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor = ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository).save(captor.capture());
        ir.ipaam.kycservices.domain.model.entity.Document saved = captor.getValue();
        assertEquals("VIDEO", saved.getType());
        assertEquals("minio-video-path", saved.getStoragePath());
        assertEquals(processInstance, saved.getProcess());
        verify(biometricStorageClient).upload(event.getDescriptor(), "VIDEO", "proc1");
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertEquals("token-for-proc1", lastVideoToken.get());
    }

    @Test
    void onSelfieUploadedEventSkipsWhenTokenGenerationFails() {
        tokenEndpointShouldFail.set(true);

        SelfieUploadedEvent event = new SelfieUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("selfie".getBytes(), "selfie-file"),
                LocalDateTime.now()
        );

        handler.on(event);

        verifyNoInteractions(biometricStorageClient);
        verify(documentRepository, never()).save(any());
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertNull(lastSelfieToken.get());
    }

    @Test
    void onSignatureUploadedEventSkipsWhenTokenGenerationFails() {
        tokenEndpointShouldFail.set(true);

        SignatureUploadedEvent event = new SignatureUploadedEvent(
                "proc1",
                "123",
                new DocumentPayloadDescriptor("signature".getBytes(), "signature-file"),
                LocalDateTime.now()
        );

        handler.on(event);

        verify(documentRepository, never()).save(any());
        assertEquals("proc1", lastTokenRequestProcessId.get());
        assertNull(lastSignatureToken.get());
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
            public Optional<ServerRequest> serverRequest() {
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
}
