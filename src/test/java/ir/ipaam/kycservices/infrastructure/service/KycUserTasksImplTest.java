package ir.ipaam.kycservices.infrastructure.service;

import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.infrastructure.repository.DocumentRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.service.impl.KycUserTasksImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KycUserTasksImplTest {

    private DocumentRepository documentRepository;
    private KycProcessInstanceRepository processInstanceRepository;
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        processInstanceRepository = mock(KycProcessInstanceRepository.class);
    }

    @Test
    void uploadCardDocumentsPersistsMetadata() {
        ProcessInstance processInstance = new ProcessInstance();
        when(processInstanceRepository.findByCamundaInstanceId("process-1"))
                .thenReturn(Optional.of(processInstance));

        ExchangeFunction exchangeFunction = request -> {
            String responseBody = "{\"front\":{\"path\":\"front-path\",\"hash\":\"front-hash\"}," +
                    "\"back\":{\"path\":\"back-path\",\"hash\":\"back-hash\"}}";
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build();
            return Mono.just(response);
        };
        webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();

        KycUserTasksImpl tasks = new KycUserTasksImpl(webClient, documentRepository, processInstanceRepository);

        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        tasks.uploadCardDocuments(front, back, "process-1");

        ArgumentCaptor<ir.ipaam.kycservices.domain.model.entity.Document> captor =
                ArgumentCaptor.forClass(ir.ipaam.kycservices.domain.model.entity.Document.class);
        verify(documentRepository, times(2)).save(captor.capture());
        List<ir.ipaam.kycservices.domain.model.entity.Document> saved = captor.getAllValues();
        assertEquals(2, saved.size());
        assertEquals(KycUserTasksImpl.DOCUMENT_TYPE_FRONT, saved.get(0).getType());
        assertEquals("front-path", saved.get(0).getStoragePath());
        assertEquals(processInstance, saved.get(0).getProcess());
        assertEquals(KycUserTasksImpl.DOCUMENT_TYPE_BACK, saved.get(1).getType());
        assertEquals("back-path", saved.get(1).getStoragePath());
    }

    @Test
    void uploadCardDocumentsValidatesInput() {
        webClient = WebClient.builder().exchangeFunction(request -> Mono.empty()).build();
        KycUserTasksImpl tasks = new KycUserTasksImpl(webClient, documentRepository, processInstanceRepository);

        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(null, back, "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(front, new byte[0], "process-1"));
        assertThrows(IllegalArgumentException.class, () -> tasks.uploadCardDocuments(front, back, " "));
        verify(documentRepository, never()).save(any());
        verify(processInstanceRepository, never()).findByCamundaInstanceId(any());
    }

    @Test
    void uploadCardDocumentsPropagatesClientErrors() {
        ExchangeFunction failingExchange = request -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        webClient = WebClient.builder().exchangeFunction(failingExchange).build();

        KycUserTasksImpl tasks = new KycUserTasksImpl(webClient, documentRepository, processInstanceRepository);

        byte[] front = "front".getBytes(StandardCharsets.UTF_8);
        byte[] back = "back".getBytes(StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> tasks.uploadCardDocuments(front, back, "process-1"));
        verify(documentRepository, never()).save(any());
        verify(processInstanceRepository, never()).findByCamundaInstanceId(any());
    }
}
