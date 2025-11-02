package ir.ipaam.kycservices.domain.model.aggregate;

import ir.ipaam.kycservices.domain.command.*;
import ir.ipaam.kycservices.domain.event.*;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.CARD_DESCRIPTORS_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.CONSENT_NOT_ACCEPTED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.EMAIL_INVALID;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.EMAIL_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ENGLISH_FIRST_NAME_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ENGLISH_LAST_NAME_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_DESCRIPTOR_LIMIT;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_DESCRIPTOR_NULL;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_DESCRIPTORS_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.KYC_NOT_STARTED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_IDENTIFIER_MISMATCH;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SELFIE_DESCRIPTOR_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SIGNATURE_DESCRIPTOR_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.TELEPHONE_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.TERMS_VERSION_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.VIDEO_DESCRIPTOR_REQUIRED;

@Aggregate
@NoArgsConstructor
@Setter
@Getter
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
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.getProcessInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        if (command.getFrontDescriptor() == null || command.getBackDescriptor() == null) {
            throw new IllegalArgumentException(CARD_DESCRIPTORS_REQUIRED);
        }

        AggregateLifecycle.apply(new CardDocumentsUploadedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getFrontDescriptor(),
                command.getBackDescriptor(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadBookletPagesCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        List<DocumentPayloadDescriptor> descriptors = command.pageDescriptors();
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalArgumentException(ID_DESCRIPTORS_REQUIRED);
        }
        if (descriptors.size() > 4) {
            throw new IllegalArgumentException(ID_DESCRIPTOR_LIMIT);
        }
        if (descriptors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(ID_DESCRIPTOR_NULL);
        }

        AggregateLifecycle.apply(new BookletPagesUploadedEvent(
                command.processInstanceId(),
                this.nationalCode,
                List.copyOf(descriptors),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(RecordTrackingNumberCommand command){
        AggregateLifecycle.apply(new RecordTrackingNumberEvent(command.getProcessInstanceId(),
                command.getTrackingNumber(),
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(UploadSelfieCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        if (command.selfieDescriptor() == null) {
            throw new IllegalArgumentException(SELFIE_DESCRIPTOR_REQUIRED);
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
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        if (command.signatureDescriptor() == null) {
            throw new IllegalArgumentException(SIGNATURE_DESCRIPTOR_REQUIRED);
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
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        if (command.videoDescriptor() == null) {
            throw new IllegalArgumentException(VIDEO_DESCRIPTOR_REQUIRED);
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
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.getProcessInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        if (command.getTermsVersion() == null || command.getTermsVersion().isBlank()) {
            throw new IllegalArgumentException(TERMS_VERSION_REQUIRED);
        }

        if (!command.isAccepted()) {
            throw new IllegalArgumentException(CONSENT_NOT_ACCEPTED);
        }

        AggregateLifecycle.apply(new ConsentAcceptedEvent(
                command.getProcessInstanceId(),
                this.nationalCode,
                command.getTermsVersion().trim(),
                true,
                LocalDateTime.now()));
    }

    @CommandHandler
    public void handle(ProvideEnglishPersonalInfoCommand command) {
        if (this.processInstanceId == null) {
            throw new IllegalStateException(KYC_NOT_STARTED);
        }

        if (!command.processInstanceId().equals(this.processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_IDENTIFIER_MISMATCH);
        }

        String firstName = normalizeRequiredText(command.firstNameEn(), ENGLISH_FIRST_NAME_REQUIRED);
        String lastName = normalizeRequiredText(command.lastNameEn(), ENGLISH_LAST_NAME_REQUIRED);
        String email = normalizeEmail(command.email());
        String telephone = normalizeRequiredText(command.telephone(), TELEPHONE_REQUIRED);

        AggregateLifecycle.apply(new EnglishPersonalInfoProvidedEvent(
                this.processInstanceId,
                this.nationalCode,
                firstName,
                lastName,
                email,
                telephone,
                LocalDateTime.now()));
    }

    private String normalizeRequiredText(String value, String messageKey) {
        if (value == null) {
            throw new IllegalArgumentException(messageKey);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(messageKey);
        }
        return trimmed;
    }

    private String normalizeEmail(String value) {
        String trimmed = normalizeRequiredText(value, EMAIL_REQUIRED);
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(EMAIL_INVALID);
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
    public void on(BookletPagesUploadedEvent event) {
        this.status = "BOOKLET_PAGES_UPLOADED";
    }

    @EventSourcingHandler
    public void on(RecordTrackingNumberEvent event) {
        this.status = "SAVE_TRACKING_NUMBER";
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
        this.firstNameEn = event.getFirstNameEn();
        this.lastNameEn = event.getLastNameEn();
        this.email = event.getEmail();
        this.telephone = event.getTelephone();
        this.status = "ENGLISH_PERSONAL_INFO_PROVIDED";
    }
}
