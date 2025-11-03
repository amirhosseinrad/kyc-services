package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.*;
import ir.ipaam.kycservices.domain.model.entity.*;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.ConsentRepository;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class KycProcessEventHandler {

    private static final Logger log = LoggerFactory.getLogger(KycProcessEventHandler.class);
    private static final String DOCUMENT_TYPE_FRONT = "CARD_FRONT";
    private static final String DOCUMENT_TYPE_BACK = "CARD_BACK";
    private static final String DOCUMENT_TYPE_SELFIE = "PHOTO";
    private static final String DOCUMENT_TYPE_VIDEO = "VIDEO";
    private static final String DOCUMENT_TYPE_SIGNATURE = "SIGNATURE";
    private static final String DOCUMENT_TYPE_BOOKLET = "BOOKLET";
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final DocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    private final MinioStorageService storageService;

    public KycProcessEventHandler(
            KycProcessInstanceRepository kycProcessInstanceRepository,
            CustomerRepository customerRepository,
            DocumentRepository documentRepository,
            ConsentRepository consentRepository,
            MinioStorageService storageService) {
        this.kycProcessInstanceRepository = kycProcessInstanceRepository;
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.consentRepository = consentRepository;
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
    public void on(BookletPagesUploadedEvent event) {
        log.info("Received BookletPagesUploadedEvent for {}", event.processInstanceId());
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
                    DOCUMENT_TYPE_BOOKLET + (i + 1),
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
                    DOCUMENT_TYPE_BOOKLET + (i + 1),
                    event.processInstanceId(),
                    processInstance);
        }

        recordSuccessfulStep(processInstance, "BOOKLET_PAGES_UPLOADED", event.uploadedAt());
    }

    @EventHandler
    public void on(RecordTrackingNumberEvent event) {
        ProcessInstance processInstance = findProcessInstance(event.getProcessInstanceId());
        if (processInstance != null) {
            processInstance.setStatus("RECORD_NATIONAL_CARD_TRACKING_NUMBER");
            StepStatus stepStatus = new StepStatus();
            stepStatus.setProcess(processInstance);
            stepStatus.setStepName("RECORD_NATIONAL_CARD_TRACKING_NUMBER");
            stepStatus.setTimestamp(event.getDate());
            stepStatus.setState(StepStatus.State.PASSED);
            List<StepStatus> statuses = processInstance.getStatuses();
            if (statuses == null) {
                statuses = new ArrayList<>();
                processInstance.setStatuses(statuses);
            }
            statuses.add(stepStatus);
            kycProcessInstanceRepository.save(processInstance);
        }
        assert processInstance != null;
        updateCustomerWithTrackingNumber(processInstance,event.getTrackingNumber());
    }


    private void updateCustomerWithTrackingNumber(ProcessInstance processInstance, String trackingNumber) {
        if (processInstance.getCustomer() == null) {
            log.warn("Process instance {} has no associated customer to update with OCR data", processInstance.getCamundaInstanceId());
            return;
        }
        var customer = processInstance.getCustomer();
        customer.setNationalCardTrackingNumber(trackingNumber);
        customerRepository.save(customer);
    }


    @EventHandler
    public void on(SelfieUploadedEvent event) {
        DocumentMetadata storageMetadata = storageService.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_SELFIE,
                event.getProcessInstanceId());

        if (storageMetadata != null) {
            storageMetadata.setInquiryDocumentId(null);
        }

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
        DocumentMetadata storageMetadata = storageService.upload(
                event.getDescriptor(),
                DOCUMENT_TYPE_VIDEO,
                event.getProcessInstanceId());

        if (storageMetadata != null) {
            storageMetadata.setInquiryDocumentId(null);
        }

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
}
