package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

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

    @Test
    void uploadCardDocumentsEmitsEventAndUpdatesState() {
        DocumentPayloadDescriptor front = new DocumentPayloadDescriptor(new byte[]{1, 2}, "front.png");
        DocumentPayloadDescriptor back = new DocumentPayloadDescriptor(new byte[]{3, 4}, "back.png");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadCardDocumentsCommand("proc-1", front, back))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(messageWithPayload(matches(event -> {
                    CardDocumentsUploadedEvent payload = (CardDocumentsUploadedEvent) event;
                    return payload.getProcessInstanceId().equals("proc-1")
                            && payload.getNationalCode().equals("123")
                            && payload.getFrontDescriptor() != null
                            && payload.getBackDescriptor() != null
                            && Arrays.equals(payload.getFrontDescriptor().data(), front.data())
                            && Arrays.equals(payload.getBackDescriptor().data(), back.data())
                            && payload.getFrontDescriptor().filename().equals("front.png")
                            && payload.getBackDescriptor().filename().equals("back.png")
                            && payload.getUploadedAt() != null;
                })))))
                .expectState(state -> assertEquals("CARD_DOCUMENTS_UPLOADED", state.getStatus()));
    }

    @Test
    void uploadCardDocumentsRequiresStartedProcess() {
        DocumentPayloadDescriptor front = new DocumentPayloadDescriptor(new byte[]{1}, "front.png");
        DocumentPayloadDescriptor back = new DocumentPayloadDescriptor(new byte[]{2}, "back.png");

        fixture.givenNoPriorActivity()
                .when(new UploadCardDocumentsCommand("proc-1", front, back))
                .expectException(IllegalStateException.class);
    }

    @Test
    void uploadCardDocumentsRequiresMatchingProcessId() {
        DocumentPayloadDescriptor front = new DocumentPayloadDescriptor(new byte[]{1}, "front.png");
        DocumentPayloadDescriptor back = new DocumentPayloadDescriptor(new byte[]{2}, "back.png");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadCardDocumentsCommand("proc-2", front, back))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void uploadCardDocumentsRequireDescriptors() {
        DocumentPayloadDescriptor front = new DocumentPayloadDescriptor(new byte[]{1}, "front.png");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadCardDocumentsCommand("proc-1", front, null))
                .expectException(IllegalArgumentException.class);

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadCardDocumentsCommand("proc-1", null, front))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void uploadSelfieEmitsEventAndUpdatesState() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1, 2, 3}, "selfie.jpg");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadSelfieCommand("proc-1", descriptor))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(messageWithPayload(matches(event -> {
                    SelfieUploadedEvent payload = (SelfieUploadedEvent) event;
                    return payload.getProcessInstanceId().equals("proc-1")
                            && payload.getNationalCode().equals("123")
                            && payload.getDescriptor() != null
                            && payload.getDescriptor().filename().equals("selfie.jpg")
                            && Arrays.equals(payload.getDescriptor().data(), descriptor.data())
                            && payload.getUploadedAt() != null;
                })))))
                .expectState(state -> assertEquals("SELFIE_UPLOADED", state.getStatus()));
    }

    @Test
    void uploadSelfieRequiresStartedProcess() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1}, "selfie.jpg");

        fixture.givenNoPriorActivity()
                .when(new UploadSelfieCommand("proc-1", descriptor))
                .expectException(IllegalStateException.class);
    }

    @Test
    void uploadSelfieRequiresMatchingProcessId() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1}, "selfie.jpg");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadSelfieCommand("proc-2", descriptor))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void uploadSelfieRequiresDescriptor() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadSelfieCommand("proc-1", null))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void uploadVideoEmitsEventAndUpdatesState() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{4, 5, 6}, "video.mp4");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadVideoCommand("proc-1", descriptor))
                .expectSuccessfulHandlerExecution()
                .expectEventsMatching(payloadsMatching(exactSequenceOf(messageWithPayload(matches(event -> {
                    VideoUploadedEvent payload = (VideoUploadedEvent) event;
                    return payload.getProcessInstanceId().equals("proc-1")
                            && payload.getNationalCode().equals("123")
                            && payload.getDescriptor() != null
                            && payload.getDescriptor().filename().equals("video.mp4")
                            && Arrays.equals(payload.getDescriptor().data(), descriptor.data())
                            && payload.getUploadedAt() != null;
                })))))
                .expectState(state -> assertEquals("VIDEO_UPLOADED", state.getStatus()));
    }

    @Test
    void uploadVideoRequiresStartedProcess() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1}, "video.mp4");

        fixture.givenNoPriorActivity()
                .when(new UploadVideoCommand("proc-1", descriptor))
                .expectException(IllegalStateException.class);
    }

    @Test
    void uploadVideoRequiresMatchingProcessId() {
        DocumentPayloadDescriptor descriptor = new DocumentPayloadDescriptor(new byte[]{1}, "video.mp4");

        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadVideoCommand("proc-2", descriptor))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    void uploadVideoRequiresDescriptor() {
        fixture.given(new KycProcessStartedEvent("proc-1", "123", LocalDateTime.now()))
                .when(new UploadVideoCommand("proc-1", null))
                .expectException(IllegalArgumentException.class);
    }
}
