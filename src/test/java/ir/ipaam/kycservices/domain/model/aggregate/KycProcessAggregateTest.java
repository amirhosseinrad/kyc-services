package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.axonframework.test.matchers.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KycProcessAggregateTest {

    private AggregateTestFixture<KycProcessAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(KycProcessAggregate.class);
    }

    @Test
    void acceptConsentEmitsEventAndUpdatesState() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new AcceptConsentCommand("proc-1", "v1", true))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(messageWithPayload(matches(event -> {
                    ConsentAcceptedEvent payload = (ConsentAcceptedEvent) event;
                    return payload.getProcessInstanceId().equals("proc-1")
                            && payload.getNationalCode().equals("123")
                            && payload.getTermsVersion().equals("v1")
                            && payload.isAccepted()
                            && payload.getAcceptedAt() != null;
                })))))
                .expectState(state -> assertEquals("CONSENT_ACCEPTED", state.getStatus()));
    }

    @Test
    void acceptConsentRequiresStartedProcess() {
        fixture.givenNoPriorActivity()
                .when(new AcceptConsentCommand("proc-1", "v1", true))
                .expectException(IllegalStateException.class);
    }

    @Test
    void acceptConsentRequiresMatchingProcessId() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new AcceptConsentCommand("proc-2", "v1", true))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void acceptConsentRequiresTermsVersion() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new AcceptConsentCommand("proc-1", " ", true))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void acceptConsentRequiresAcceptance() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new AcceptConsentCommand("proc-1", "v1", false))
                .expectException(IllegalArgumentException.class);
    }
}
