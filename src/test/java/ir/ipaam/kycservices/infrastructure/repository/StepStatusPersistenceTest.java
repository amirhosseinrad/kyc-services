package ir.ipaam.kycservices.infrastructure.repository;

import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.handler.KycProcessEventHandler;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import ir.ipaam.kycservices.infrastructure.service.dto.DocumentMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
class StepStatusPersistenceTest {

    @Autowired
    private KycProcessInstanceRepository processInstanceRepository;

    @Autowired
    private KycStepStatusRepository stepStatusRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void cardUploadEventPersistsSingleStepStatusHistoryRow() {
        ProcessInstance instance = new ProcessInstance();
        instance.setCamundaInstanceId("proc-integration");
        processInstanceRepository.save(instance);

        entityManager.flush();
        entityManager.clear();

        MinioStorageService storageService = mock(MinioStorageService.class);
        DocumentMetadata frontMetadata = new DocumentMetadata();
        frontMetadata.setPath("card/front/path");
        frontMetadata.setHash("front-hash");
        frontMetadata.setBranded(true);
        DocumentMetadata backMetadata = new DocumentMetadata();
        backMetadata.setPath("card/back/path");
        backMetadata.setHash("back-hash");
        backMetadata.setBranded(true);

        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("CARD_FRONT"), eq("proc-integration")))
                .thenReturn(frontMetadata);
        when(storageService.upload(any(DocumentPayloadDescriptor.class), eq("CARD_BACK"), eq("proc-integration")))
                .thenReturn(backMetadata);

        KycProcessEventHandler handler = new KycProcessEventHandler(
                processInstanceRepository,
                mock(CustomerRepository.class),
                stepStatusRepository,
                documentRepository,
                mock(ConsentRepository.class),
                WebClient.builder().baseUrl("http://localhost").build(),
                mock(InquiryTokenService.class),
                storageService);

        CardDocumentsUploadedEvent event = new CardDocumentsUploadedEvent(
                "proc-integration",
                "nc-001",
                new DocumentPayloadDescriptor(new byte[]{1}, "front-file"),
                new DocumentPayloadDescriptor(new byte[]{2}, "back-file"),
                LocalDateTime.now());

        handler.on(event);

        entityManager.flush();
        entityManager.clear();

        List<StepStatus> persistedStatuses = stepStatusRepository.findAll();
        assertThat(persistedStatuses)
                .hasSize(1)
                .first()
                .satisfies(status -> {
                    assertThat(status.getStepName()).isEqualTo("CARD_DOCUMENTS_UPLOADED");
                    assertThat(status.getState()).isEqualTo(StepStatus.State.PASSED);
                    assertThat(status.getProcess().getCamundaInstanceId()).isEqualTo("proc-integration");
                });
    }
}
