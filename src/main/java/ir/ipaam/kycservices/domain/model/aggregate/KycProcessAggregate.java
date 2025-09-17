package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.KycProcessStartedEvent;
import ir.ipaam.kycservices.domain.event.KycStatusUpdatedEvent;
import ir.ipaam.kycservices.domain.event.SelfieUploadedEvent;
import ir.ipaam.kycservices.domain.event.SignatureUploadedEvent;
import ir.ipaam.kycservices.domain.event.VideoUploadedEvent;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;

import java.time.LocalDateTime;

@Aggregate
@NoArgsConstructor
public class KycProcessAggregate {

    @AggregateIdentifier
    private String processInstanceId;
    private String nationalCode;
    private String status;

    @CommandHandler
    public KycProcessAggregate(StartKycProcessCommand command) {
        AggregateLifecycle.apply(new KycProcessStartedEvent(
                command.getProcessInstanceId(),
                command.getNationalCode(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UpdateKycStatusCommand command) {
        AggregateLifecycle.apply(new KycStatusUpdatedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getStatus(),
                command.getStepName(),
                command.getState(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadCardDocumentsCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.getProcessInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.getFrontDescriptor() == null || command.getBackDescriptor() == null) {
            throw new IllegalArgumentException("Document descriptors must be provided");
        }

        AggregateLifecycle.apply(new CardDocumentsUploadedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getFrontDescriptor(),
                command.getBackDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadSelfieCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.selfieDescriptor() == null) {
            throw new IllegalArgumentException("Selfie descriptor must be provided");
        }

        AggregateLifecycle.apply(new SelfieUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.selfieDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadSignatureCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.signatureDescriptor() == null) {
            throw new IllegalArgumentException("Signature descriptor must be provided");
        }

        AggregateLifecycle.apply(new SignatureUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.signatureDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadVideoCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.videoDescriptor() == null) {
            throw new IllegalArgumentException("Video descriptor must be provided");
        }

        AggregateLifecycle.apply(new VideoUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.videoDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(AcceptConsentCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.getProcessInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.getTermsVersion() == null || command.getTermsVersion().isBlank()) {
            throw new IllegalArgumentException("termsVersion must be provided");
        }

        if (!command.isAccepted()) {
            throw new IllegalArgumentException("Consent must be accepted");
        }

        AggregateLifecycle.apply(new ConsentAcceptedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getTermsVersion().trim(),
                command.isAccepted(),
                LocalDateTime.now()));
    }

    @EventSourcingHandler
    public void on(KycProcessStartedEvent event) {
        this.processInstanceId = event.getProcessInstanceId();
        this.nationalCode = event.getNationalCode();
        this.status = "STARTED";
    }

    @EventSourcingHandler
    public void on(KycStatusUpdatedEvent event) {
        this.status = event.getStatus();
    }

    @EventSourcingHandler
    public void on(CardDocumentsUploadedEvent event) {
        this.status = "CARD_DOCUMENTS_UPLOADED";
    }

    @EventSourcingHandler
    public void on(SelfieUploadedEvent event) {
        this.status = "SELFIE_UPLOADED";
    }

    @EventSourcingHandler
    public void on(SignatureUploadedEvent event) {
        this.status = "SIGNATURE_UPLOADED";
    }

    @EventSourcingHandler
    public void on(VideoUploadedEvent event) {
        this.status = "VIDEO_UPLOADED";
    }

    @EventSourcingHandler
    public void on(ConsentAcceptedEvent event) {
        this.status = "CONSENT_ACCEPTED";
    }
}
