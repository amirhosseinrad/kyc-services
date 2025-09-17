package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
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
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import ir.ipaam.kycservices.infrastructure.service.dto.UploadCardDocumentsResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class KycProcessEventHandler {

    private static final Logger log = LoggerFactory.getLogger(KycProcessEventHandler.class);
    private static final String DOCUMENT_TYPE_FRONT = "CARD_FRONT";
    private static final String DOCUMENT_TYPE_BACK = "CARD_BACK";

    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final DocumentRepository documentRepository;
    private final ConsentRepository consentRepository;
    @Qualifier("cardDocumentWebClient")
    private final WebClient documentWebClient;

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

        ProcessInstance processInstance = kycProcessInstanceRepository
                .findByCamundaInstanceId(event.getProcessInstanceId())
                .orElse(null);
        if (processInstance == null) {
            log.warn("No persisted process instance found for Camunda id {}", event.getProcessInstanceId());
        }

        persistMetadata(response.getFront(), DOCUMENT_TYPE_FRONT, event.getProcessInstanceId(), processInstance);
        persistMetadata(response.getBack(), DOCUMENT_TYPE_BACK, event.getProcessInstanceId(), processInstance);
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

    private ByteArrayResource asResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
