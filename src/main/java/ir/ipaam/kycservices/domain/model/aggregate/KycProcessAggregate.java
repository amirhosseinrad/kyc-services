package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.AcceptConsentCommand;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
import ir.ipaam.kycservices.domain.command.StartKycProcessCommand;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.domain.event.CardDocumentsUploadedEvent;
import ir.ipaam.kycservices.domain.event.ConsentAcceptedEvent;
import ir.ipaam.kycservices.domain.event.EnglishPersonalInfoProvidedEvent;
import ir.ipaam.kycservices.domain.event.IdPagesUploadedEvent;
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
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Aggregate
@NoArgsConstructor
public class KycProcessAggregate {

    @AggregateIdentifier
    private String processInstanceId;
    private String nationalCode;
    private String status;
    private String firstNameEn;
    private String lastNameEn;
    private String email;
    private String telephone;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\\s]+@[^@\\\s]+\\.[^@\\\s]+$");

    @CommandHandler
    public KycProcessAggregate(StartKycProcessCommand command) {
        AggregateLifecycle.apply(new KycProcessStartedEvent(
                command.processInstanceId(),
                command.nationalCode(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UpdateKycStatusCommand command) {
        AggregateLifecycle.apply(new KycStatusUpdatedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.status(),
                command.stepName(),
                command.state(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadCardDocumentsCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.frontDescriptor() == null || command.backDescriptor() == null) {
            throw new IllegalArgumentException("Document descriptors must be provided");
        }

        AggregateLifecycle.apply(new CardDocumentsUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.frontDescriptor(),
                command.backDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadIdPagesCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        List<DocumentPayloadDescriptor> descriptors = command.pageDescriptors();
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalArgumentException("At least one ID page descriptor must be provided");
        }
        if (descriptors.size() > 4) {
            throw new IllegalArgumentException("No more than four ID page descriptors are allowed");
        }
        if (descriptors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ID page descriptors must not be null");
        }

        AggregateLifecycle.apply(new IdPagesUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                List.copyOf(descriptors),
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

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        if (command.termsVersion() == null || command.termsVersion().isBlank()) {
            throw new IllegalArgumentException("termsVersion must be provided");
        }

        if (!command.accepted()) {
            throw new IllegalArgumentException("Consent must be accepted");
        }

        AggregateLifecycle.apply(new ConsentAcceptedEvent(
                command.processInstanceId(),
                this.nationalCode,
                command.termsVersion().trim(),
                command.accepted(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(ProvideEnglishPersonalInfoCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException("KYC process has not been started");
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException("Process instance identifier mismatch");
        }

        String firstName = normalizeRequiredText(command.firstNameEn(), "firstNameEn");
        String lastName = normalizeRequiredText(command.lastNameEn(), "lastNameEn");
        String email = normalizeEmail(command.email());
        String telephone = normalizeRequiredText(command.telephone(), "telephone");

        AggregateLifecycle.apply(new EnglishPersonalInfoProvidedEvent(
                this.processInstanceId,
                this.nationalCode,
                firstName,
                lastName,
                email,
                telephone,
                LocalDateTime.now()));
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return trimmed;
    }

    private String normalizeEmail(String value) {
        String trimmed = normalizeRequiredText(value, "email");
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("email must be a valid email address");
        }
        return trimmed;
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
    public void on(IdPagesUploadedEvent event) {
        this.status = "ID_PAGES_UPLOADED";
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

    @EventSourcingHandler
    public void on(EnglishPersonalInfoProvidedEvent event) {
        this.firstNameEn = event.firstNameEn();
        this.lastNameEn = event.lastNameEn();
        this.email = event.email();
        this.telephone = event.telephone();
        this.status = "ENGLISH_PERSONAL_INFO_PROVIDED";
    }
}
