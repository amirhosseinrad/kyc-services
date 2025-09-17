package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.SignatureUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.Consent;
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.service.BiometricStorageClient;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import ir.ipaam.kycservices.infrastructure.service.dto.InquiryUploadResponse;
import ir.ipaam.kycservices.infrastructure.service.dto.SaveSignatureRequest;
import ir.ipaam.kycservices.infrastructure.service.dto.SaveSignatureResponse;
import ir.ipaam.kycservices.infrastructure.service.dto.UploadCardDocumentsResponse;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Component
public class KycProcessEventHandler {

    private static final Logger log = LoggerFactory.getLogger(KycProcessEventHandler.class);
    private static final String DOCUMENT_TYPE_FRONT = "CARD_FRONT";
    private static final String DOCUMENT_TYPE_BACK = "CARD_BACK";
    private static final String DOCUMENT_TYPE_SELFIE = "PHOTO";
    private static final String DOCUMENT_TYPE_VIDEO = "VIDEO";
    private static final String DOCUMENT_TYPE_SIGNATURE = "SIGNATURE";
    private static final String DEFAULT_THRESHOLD = "-1";
    private static final String DEFAULT_RANDOM_TEXT = "";

    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final DocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    private final WebClient documentWebClient;
    private final WebClient inquiryWebClient;
    private final BiometricStorageClient biometricStorageClient;

    public KycProcessEventHandler(
            KycProcessInstanceRepository kycProcessInstanceRepository,
            CustomerRepository customerRepository,
            KycStepStatusRepository kycStepStatusRepository,
            DocumentRepository documentRepository,
            ConsentRepository consentRepository,
            @Qualifier("cardDocumentWebClient") WebClient documentWebClient,
            @Qualifier("inquiryWebClient") WebClient inquiryWebClient,
            BiometricStorageClient biometricStorageClient) {
        this.kycProcessInstanceRepository = kycProcessInstanceRepository;
        this.customerRepository = customerRepository;
        this.kycStepStatusRepository = kycStepStatusRepository;
        this.documentRepository = documentRepository;
        this.consentRepository = consentRepository;
        this.documentWebClient = documentWebClient;
        this.inquiryWebClient = inquiryWebClient;
        this.biometricStorageClient = biometricStorageClient;
    }

    @EventHandler
    public void on(KycProcessStartedEvent event) {
        Customer customer = customerRepository.findByNationalCode(event.getNationalCode())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setNationalCode(event.getNationalCode());
                    return customerRepository.save(c);
                });

        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId(event.getProcessInstanceId());
        instance.setStatus("STARTED");
        instance.setStartedAt(LocalDateTime.now());
        instance.setCustomer(customer);

        kycProcessInstanceRepository.save(instance);
    }

    @EventHandler
    public void on(KycStatusUpdatedEvent event) {
        kycProcessInstanceRepository.findByCamundaInstanceId(event.getProcessInstanceId())
                .ifPresent(instance -> {
                    instance.setStatus(event.getStatus());

                    StepStatus stepStatus = new StepStatus();
                    stepStatus.setProcess(instance);
                    stepStatus.setStepName(event.getStepName());
                    stepStatus.setTimestamp(event.getUpdatedAt());
                    if (event.getState() != null) {
                        stepStatus.setState(StepStatus.State.valueOf(event.getState().toUpperCase(Locale.ROOT)));
                    }

                    instance.getStatuses().add(stepStatus);

                    kycProcessInstanceRepository.save(instance);
                    kycStepStatusRepository.save(stepStatus);
                });
    }

    @EventHandler
    public void on(CardDocumentsUploadedEvent event) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("frontImage", asResource(event.getFrontDescriptor().data(), event.getFrontDescriptor().filename()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("backImage", asResource(event.getBackDescriptor().data(), event.getBackDescriptor().filename()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("processInstanceId", event.getProcessInstanceId());

        UploadCardDocumentsResponse response = documentWebClient.post()
                .uri("/documents/card")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(UploadCardDocumentsResponse.class)
                .onErrorResume(throwable -> {
                    log.error("Failed to upload card documents for process {}", event.getProcessInstanceId(), throwable);
                    return Mono.error(throwable);
                })
                .block();

        if (response == null) {
            log.warn("Document storage service returned empty response for process {}", event.getProcessInstanceId());
            return;
        }

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());

        persistMetadata(response.getFront(), DOCUMENT_TYPE_FRONT, event.getProcessInstanceId(), processInstance);
        persistMetadata(response.getBack(), DOCUMENT_TYPE_BACK, event.getProcessInstanceId(), processInstance);
    }

    @EventHandler
    public void on(SelfieUploadedEvent event) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("tokenValue", event.getProcessInstanceId());
        bodyBuilder.part("faceThreshold", DEFAULT_THRESHOLD);
        bodyBuilder.part("fileData", asResource(event.getDescriptor().data(), event.getDescriptor().filename()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        DocumentMetadata metadata = uploadInquiryDocument(
                "/api/Inquiry/SendProbImageByToken",
                bodyBuilder,
                event.getProcessInstanceId(),
                "selfie");

        if (metadata == null) {
            return;
        }

        DocumentMetadata storageMetadata = biometricStorageClient.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_SELFIE,
                event.getProcessInstanceId());

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        persistMetadata(storageMetadata, DOCUMENT_TYPE_SELFIE, event.getProcessInstanceId(), processInstance);
    }

    @EventHandler
    public void on(SignatureUploadedEvent event) {
        SaveSignatureRequest.FileData fileData = new SaveSignatureRequest.FileData(
                "FileData",
                event.getDescriptor().filename(),
                Base64.getEncoder().encodeToString(event.getDescriptor().data()));

        SaveSignatureRequest request = new SaveSignatureRequest(event.getProcessInstanceId(), fileData);

        SaveSignatureResponse response = inquiryWebClient.post()
                .uri("/api/Inquiry/SaveSignature")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(SaveSignatureResponse.class)
                .onErrorResume(throwable -> {
                    log.error("Failed to upload signature for process {}", event.getProcessInstanceId(), throwable);
                    return Mono.error(throwable);
                })
                .block();

        if (response == null) {
            log.warn("Inquiry service returned empty response for process {} when uploading signature", event.getProcessInstanceId());
            return;
        }

        Integer responseCode = response.getResponseCode();
        if (responseCode != null && responseCode != 0) {
            String message = response.getResponseMessage();
            if (response.getException() != null && response.getException().getErrorMessage() != null) {
                message = response.getException().getErrorMessage();
            }
            log.error("Inquiry service reported error code {} for process {} when uploading signature: {}",
                    responseCode, event.getProcessInstanceId(), message);
            return;
        }

        String signatureId = extractSignatureId(response.getResult());

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setPath(signatureId);
        persistMetadata(metadata, DOCUMENT_TYPE_SIGNATURE, event.getProcessInstanceId(), processInstance);
    }

    @EventHandler
    public void on(VideoUploadedEvent event) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("tokenValue", event.getProcessInstanceId());
        bodyBuilder.part("randomText", DEFAULT_RANDOM_TEXT);
        bodyBuilder.part("faceThreshold", DEFAULT_THRESHOLD);
        bodyBuilder.part("voiceThreshold", DEFAULT_THRESHOLD);
        bodyBuilder.part("fileData", asResource(event.getDescriptor().data(), event.getDescriptor().filename()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        DocumentMetadata metadata = uploadInquiryDocument(
                "/api/Inquiry/SendProbVideoByToken",
                bodyBuilder,
                event.getProcessInstanceId(),
                "video");

        if (metadata == null) {
            return;
        }

        DocumentMetadata storageMetadata = biometricStorageClient.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_VIDEO,
                event.getProcessInstanceId());

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        persistMetadata(storageMetadata, DOCUMENT_TYPE_VIDEO, event.getProcessInstanceId(), processInstance);
    }

    @EventHandler
    public void on(ConsentAcceptedEvent event) {
        kycProcessInstanceRepository.findByCamundaInstanceId(event.getProcessInstanceId())
                .ifPresentOrElse(instance -> {
                    instance.setStatus("CONSENT_ACCEPTED");
                    kycProcessInstanceRepository.save(instance);

                    Consent consent = new Consent();
                    consent.setProcess(instance);
                    consent.setAccepted(event.isAccepted());
                    consent.setAcceptedAt(event.getAcceptedAt());
                    consent.setTermsVersion(event.getTermsVersion());
                    consentRepository.save(consent);
                }, () -> log.warn("No persisted process instance found for Camunda id {}", event.getProcessInstanceId()));
    }

    @QueryHandler
    public ProcessInstance handle(FindKycStatusQuery query) {
        return kycProcessInstanceRepository
                .findTopByCustomer_NationalCodeOrderByStartedAtDesc(query.nationalCode())
                .orElseGet(() -> {
                    ProcessInstance instance = new ProcessInstance();
                    instance.setStatus("UNKNOWN");
                    return instance;
                });
    }

    private DocumentMetadata uploadInquiryDocument(String uri, MultipartBodyBuilder bodyBuilder,
                                                   String processInstanceId, String logContext) {
        InquiryUploadResponse response = inquiryWebClient.post()
                .uri(uri)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(InquiryUploadResponse.class)
                .onErrorResume(throwable -> {
                    log.error("Failed to upload {} document for process {}", logContext, processInstanceId, throwable);
                    return Mono.error(throwable);
                })
                .block();

        if (response == null) {
            log.warn("Inquiry service returned empty response for process {} when uploading {}", processInstanceId, logContext);
            return null;
        }

        Integer responseCode = response.getResponseCode();
        if (responseCode != null && responseCode != 0) {
            String message = response.getResponseMessage();
            if (response.getException() != null && response.getException().getErrorMessage() != null) {
                message = response.getException().getErrorMessage();
            }
            log.error("Inquiry service reported error code {} for process {} when uploading {}: {}",
                    responseCode, processInstanceId, logContext, message);
            return null;
        }

        return response.getResult();
    }

    private ProcessInstance findProcessInstance(String processInstanceId) {
        ProcessInstance processInstance = kycProcessInstanceRepository
                .findByCamundaInstanceId(processInstanceId)
                .orElse(null);
        if (processInstance == null) {
            log.warn("No persisted process instance found for Camunda id {}", processInstanceId);
        }
        return processInstance;
    }

    private void persistMetadata(DocumentMetadata metadata, String type, String processInstanceId,
                                 ProcessInstance processInstance) {
        if (metadata == null) {
            log.warn("Storage metadata for {} document is missing for process {}", type, processInstanceId);
            return;
        }

        Document document = new Document();
        document.setType(type);
        document.setStoragePath(metadata.getPath());
        document.setHash(metadata.getHash());
        document.setVerified(false);
        document.setProcess(processInstance);
        documentRepository.save(document);
        log.info("Persisted document metadata for type {} at path {} for process {}", type, metadata.getPath(), processInstanceId);
    }

    private String extractSignatureId(String resultMessage) {
        if (resultMessage == null) {
            return null;
        }
        int index = resultMessage.lastIndexOf(':');
        if (index >= 0 && index < resultMessage.length() - 1) {
            return resultMessage.substring(index + 1).trim();
        }
        return resultMessage.trim();
    }

    private ByteArrayResource asResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
