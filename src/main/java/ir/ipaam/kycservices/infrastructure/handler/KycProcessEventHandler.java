package ir.ipaam.kycservices.infrastructure.handler;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.INQUIRY_TOKEN_FAILED;

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
import ir.ipaam.kycservices.domain.model.entity.Document;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import ir.ipaam.kycservices.infrastructure.service.dto.InquiryUploadResponse;
import ir.ipaam.kycservices.infrastructure.service.dto.SavePersonDocumentRequest;
import ir.ipaam.kycservices.infrastructure.service.dto.SavePersonDocumentResponse;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class KycProcessEventHandler {

    private static final Logger log = LoggerFactory.getLogger(KycProcessEventHandler.class);
    private static final String DOCUMENT_TYPE_FRONT = "CARD_FRONT";
    private static final String DOCUMENT_TYPE_BACK = "CARD_BACK";
    private static final String DOCUMENT_TYPE_SELFIE = "PHOTO";
    private static final String DOCUMENT_TYPE_VIDEO = "VIDEO";
    private static final String DOCUMENT_TYPE_SIGNATURE = "SIGNATURE";
    private static final String DOCUMENT_TYPE_ID_PAGE_PREFIX = "ID_PAGE_";
    private static final String DEFAULT_THRESHOLD = "-1";
    private static final String DEFAULT_RANDOM_TEXT = "";
    private static final String FILE_DATA_PART_NAME = "FileData";
    private static final int INQUIRY_DOCUMENT_TYPE_ID_PAGE_BASE = 201;

    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final DocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    private final WebClient inquiryWebClient;
    private final InquiryTokenService inquiryTokenService;
    private final MinioStorageService storageService;

    public KycProcessEventHandler(
            KycProcessInstanceRepository kycProcessInstanceRepository,
            CustomerRepository customerRepository,
            KycStepStatusRepository kycStepStatusRepository,
            DocumentRepository documentRepository,
            ConsentRepository consentRepository,
            @Qualifier("inquiryWebClient") WebClient inquiryWebClient,
            InquiryTokenService inquiryTokenService,
            MinioStorageService storageService) {
        this.kycProcessInstanceRepository = kycProcessInstanceRepository;
        this.customerRepository = customerRepository;
        this.kycStepStatusRepository = kycStepStatusRepository;
        this.documentRepository = documentRepository;
        this.consentRepository = consentRepository;
        this.inquiryWebClient = inquiryWebClient;
        this.inquiryTokenService = inquiryTokenService;
        this.storageService = storageService;
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
                        StepStatus.State state = StepStatus.State.valueOf(event.getState().toUpperCase(Locale.ROOT));
                        stepStatus.setState(state);
                        if (state == StepStatus.State.FAILED) {
                            stepStatus.setErrorCause(event.getStatus());
                        } else {
                            stepStatus.setErrorCause(null);
                        }
                    } else {
                        stepStatus.setErrorCause(null);
                    }

                    instance.getStatuses().add(stepStatus);

                    kycProcessInstanceRepository.save(instance);
                });
    }

    @EventHandler
    public void on(CardDocumentsUploadedEvent event) {
        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        DocumentMetadata frontMetadata = storageService.upload(
                event.getFrontDescriptor(),
                DOCUMENT_TYPE_FRONT,
                event.getProcessInstanceId());
        if (frontMetadata != null) {
            frontMetadata.setInquiryDocumentId(null);
        }
        persistMetadata(frontMetadata, DOCUMENT_TYPE_FRONT, event.getProcessInstanceId(), processInstance);

        DocumentMetadata backMetadata = storageService.upload(
                event.getBackDescriptor(),
                DOCUMENT_TYPE_BACK,
                event.getProcessInstanceId());
        if (backMetadata != null) {
            backMetadata.setInquiryDocumentId(null);
        }
        persistMetadata(backMetadata, DOCUMENT_TYPE_BACK, event.getProcessInstanceId(), processInstance);

        recordSuccessfulStep(processInstance, "CARD_DOCUMENTS_UPLOADED", event.getUploadedAt());
    }

    @EventHandler
    public void on(EnglishPersonalInfoProvidedEvent event) {
        Optional<ProcessInstance> processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(event.getProcessInstanceId());

        Customer customer = processInstance
                .map(ProcessInstance::getCustomer)
                .orElseGet(() -> customerRepository.findByNationalCode(event.getNationalCode())
                        .orElseGet(() -> {
                            Customer c = new Customer();
                            c.setNationalCode(event.getNationalCode());
                            return c;
                        }));

        updateCustomerInfo(customer, event);
        customerRepository.save(customer);

        processInstance.ifPresent(instance -> {
            if (instance.getCustomer() == null) {
                instance.setCustomer(customer);
            }
            instance.setStatus("ENGLISH_PERSONAL_INFO_PROVIDED");
            instance.setCompletedAt(event.getProvidedAt());
            recordSuccessfulStep(processInstance.get(), "ENGLISH_PERSONAL_INFO_PROVIDED", event.getProvidedAt());
            kycProcessInstanceRepository.save(instance);
        });
    }

    private void updateCustomerInfo(Customer customer, EnglishPersonalInfoProvidedEvent event) {
        customer.setFirstName(event.getFirstNameEn());
        customer.setLastName(event.getLastNameEn());
        customer.setEmail(event.getEmail());
        customer.setMobile(event.getTelephone());
    }

    @EventHandler
    public void on(IdPagesUploadedEvent event) {
        List<DocumentPayloadDescriptor> descriptors = event.pageDescriptors();
        if (descriptors == null || descriptors.isEmpty()) {
            log.warn("Received ID pages event without descriptors for process {}", event.processInstanceId());
            return;
        }
        List<String> inquiryIds = new ArrayList<>(Collections.nCopies(descriptors.size(), null));
        ProcessInstance processInstance = findProcessInstance(event.processInstanceId());
        List<DocumentMetadata> metadataList = new ArrayList<>(descriptors.size());
        for (int i = 0; i < descriptors.size(); i++) {
            DocumentMetadata metadata = storageService.upload(
                    descriptors.get(i),
                    DOCUMENT_TYPE_ID_PAGE_PREFIX + (i + 1),
                    event.processInstanceId());
            metadataList.add(metadata);
        }
        for (int i = 0; i < descriptors.size(); i++) {
            DocumentMetadata metadata = i < metadataList.size() ? metadataList.get(i) : null;
            if (metadata != null) {
                metadata.setInquiryDocumentId(inquiryIds.get(i));
            }
            persistMetadata(
                    metadata,
                    DOCUMENT_TYPE_ID_PAGE_PREFIX + (i + 1),
                    event.processInstanceId(),
                    processInstance);
        }

        recordSuccessfulStep(processInstance, "ID_PAGES_UPLOADED", event.uploadedAt());
    }

    @EventHandler
    public void on(SelfieUploadedEvent event) {
        Optional<String> tokenOptional;
        try {
            tokenOptional = inquiryTokenService.generateToken(event.getProcessInstanceId());
        } catch (InquiryTokenException ex) {
            log.warn("Failed to generate inquiry token for selfie step of process {}", event.getProcessInstanceId(), ex);
            ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
            recordFailedStep(processInstance, "SELFIE_UPLOADED", event.getUploadedAt(), ex.getMessage());
            return;
        }

        if (tokenOptional.isEmpty()) {
            log.warn("Inquiry token service returned empty token for selfie step of process {}", event.getProcessInstanceId());
            ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
            recordFailedStep(processInstance, "SELFIE_UPLOADED", event.getUploadedAt(), INQUIRY_TOKEN_FAILED);
            return;
        }

        String token = tokenOptional.get();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("tokenValue", token);
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

        DocumentMetadata storageMetadata = storageService.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_SELFIE,
                event.getProcessInstanceId());

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        persistMetadata(storageMetadata, DOCUMENT_TYPE_SELFIE, event.getProcessInstanceId(), processInstance);

        recordSuccessfulStep(processInstance, "SELFIE_UPLOADED", event.getUploadedAt());
    }

    @EventHandler
    public void on(SignatureUploadedEvent event) {
        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        if (processInstance != null) {
            processInstance.setStatus("SIGNATURE_UPLOADED");

            StepStatus stepStatus = new StepStatus();
            stepStatus.setProcess(processInstance);
            stepStatus.setStepName("SIGNATURE_UPLOADED");
            stepStatus.setTimestamp(event.getUploadedAt());
            stepStatus.setState(StepStatus.State.PASSED);

            List<StepStatus> statuses = processInstance.getStatuses();
            if (statuses == null) {
                statuses = new ArrayList<>();
                processInstance.setStatuses(statuses);
            }
            statuses.add(stepStatus);

            kycProcessInstanceRepository.save(processInstance);
        }

        DocumentMetadata metadata = storageService.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_SIGNATURE,
                event.getProcessInstanceId());
        if (metadata != null) {
            metadata.setInquiryDocumentId(null);
        }

        persistMetadata(metadata, DOCUMENT_TYPE_SIGNATURE, event.getProcessInstanceId(), processInstance);
    }

    @EventHandler
    public void on(VideoUploadedEvent event) {
        String token = event.getInquiryToken();
        if (token == null || token.isBlank()) {
            log.warn("Skipping video upload for process {} because inquiry token could not be generated", event.getProcessInstanceId());
            ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
            recordFailedStep(processInstance, "VIDEO_UPLOADED", event.getUploadedAt(), INQUIRY_TOKEN_FAILED);
            return;
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("tokenValue", token);
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

        DocumentMetadata storageMetadata = storageService.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_VIDEO,
                event.getProcessInstanceId());

        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        persistMetadata(storageMetadata, DOCUMENT_TYPE_VIDEO, event.getProcessInstanceId(), processInstance);

        recordSuccessfulStep(processInstance, "VIDEO_UPLOADED", event.getUploadedAt());
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
        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        recordSuccessfulStep(processInstance, "CONSENT_ACCEPTED", event.getAcceptedAt());

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
        document.setInquiryDocumentId(metadata.getInquiryDocumentId());
        document.setVerified(metadata.isBranded());
        document.setEncrypted(metadata.isEncrypted());
        document.setEncryptionIv(metadata.getEncryptionIv());
        document.setProcess(processInstance);
        documentRepository.save(document);
        log.info("Persisted document metadata for type {} at path {} for process {} (verified={})",
                type,
                metadata.getPath(),
                processInstanceId,
                document.isVerified());
        if (!document.isVerified()) {
            log.debug("Document {} for process {} is not verified because branding was skipped or failed", type, processInstanceId);
        }
    }

    private void recordSuccessfulStep(ProcessInstance processInstance, String status, LocalDateTime timestamp) {
        if (processInstance == null) {
            return;
        }

        processInstance.setStatus(status);

        StepStatus stepStatus = new StepStatus();
        stepStatus.setProcess(processInstance);
        stepStatus.setStepName(status);
        stepStatus.setTimestamp(timestamp);
        stepStatus.setState(StepStatus.State.PASSED);
        stepStatus.setErrorCause(null);

        List<StepStatus> statuses = processInstance.getStatuses();
        if (statuses == null) {
            statuses = new ArrayList<>();
            processInstance.setStatuses(statuses);
        }
        statuses.add(stepStatus);

        kycProcessInstanceRepository.save(processInstance);
    }

    private void recordFailedStep(ProcessInstance processInstance, String status, LocalDateTime timestamp, String errorCause) {
        if (processInstance == null) {
            return;
        }

        processInstance.setStatus(status + "_FAILED");

        StepStatus stepStatus = new StepStatus();
        stepStatus.setProcess(processInstance);
        stepStatus.setStepName(status);
        stepStatus.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());
        stepStatus.setState(StepStatus.State.FAILED);
        String cause = (errorCause != null && !errorCause.isBlank()) ? errorCause : INQUIRY_TOKEN_FAILED;
        stepStatus.setErrorCause(cause);

        List<StepStatus> statuses = processInstance.getStatuses();
        if (statuses == null) {
            statuses = new ArrayList<>();
            processInstance.setStatuses(statuses);
        }
        statuses.add(stepStatus);

        kycProcessInstanceRepository.save(processInstance);
    }

    private String extractResultId(String resultMessage) {
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
