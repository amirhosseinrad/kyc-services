package ir.ipaam.kycservices.infrastructure.handler;

import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycProcessEventHandlerTest {

    private final KycProcessInstanceRepository instanceRepository = mock(KycProcessInstanceRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final KycProcessEventHandler handler = new KycProcessEventHandler(instanceRepository, customerRepository);

    @Test
    void onEventCreatesProcessInstance() {
        when(customerRepository.findByNationalCode("123")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KycProcessStartedEvent event = new KycProcessStartedEvent("proc1", "123", LocalDateTime.now());
        handler.on(event);

        verify(instanceRepository).save(any(KycProcessInstance.class));
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
