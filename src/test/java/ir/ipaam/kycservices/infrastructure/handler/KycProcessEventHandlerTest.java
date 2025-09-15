package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.KycStepStatus;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycProcessEventHandlerTest {

    private final KycProcessInstanceRepository instanceRepository = mock(KycProcessInstanceRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final KycStepStatusRepository stepStatusRepository = mock(KycStepStatusRepository.class);
    private final KycProcessEventHandler handler = new KycProcessEventHandler(instanceRepository, customerRepository, stepStatusRepository);

    @Test
    void onEventCreatesProcessInstance() {
        when(customerRepository.findByNationalCode("123")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycProcessStartedEvent event = new KycProcessStartedEvent("proc1", "123", LocalDateTime.now());
        handler.on(event);

        verify(instanceRepository).save(any(KycProcessInstance.class));
    }

    @Test
    void onStatusUpdatedEventUpdatesProcessInstance() {
        KycProcessInstance instance = new KycProcessInstance();

        when(instanceRepository.findByCamundaInstanceId("proc1")).thenReturn(Optional.of(instance));
        when(stepStatusRepository.save(any(KycStepStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        ArgumentCaptor<KycStepStatus> captor = ArgumentCaptor.forClass(KycStepStatus.class);
        verify(stepStatusRepository).save(captor.capture());
        KycStepStatus savedStatus = captor.getValue();
        assertEquals("DOCUMENT_VERIFICATION", savedStatus.getStepName());
        assertEquals(KycStepStatus.State.PASSED, savedStatus.getState());
        assertEquals(timestamp, savedStatus.getTimestamp());
        assertEquals(instance, savedStatus.getProcess());
        assertTrue(instance.getStatuses().contains(savedStatus));
    }

    @Test
    void queryReturnsPersistedInstance() {
        KycProcessInstance instance = new KycProcessInstance();
        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.of(instance));

        KycProcessInstance result = handler.handle(new FindKycStatusQuery("123"));
        assertEquals(instance, result);
    }

    @Test
    void queryReturnsUnknownWhenNotFound() {
        when(instanceRepository.findTopByCustomer_NationalCodeOrderByStartedAtDesc("123"))
                .thenReturn(Optional.empty());

        KycProcessInstance result = handler.handle(new FindKycStatusQuery("123"));
        assertNotNull(result);
        assertEquals("UNKNOWN", result.getStatus());
    }
}
