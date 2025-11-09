package ir.ipaam.kycservices.application.api.error;

public final class ErrorMessageKeys {

    private ErrorMessageKeys() {
    }

    public static final String VALIDATION_FAILED = "error.validation.failed";
    public static final String NATIONAL_CODE_INVALID = "error.nationalCode.invalid";
    public static final String COMMAND_EXECUTION_FAILED = "error.command.execution";
    public static final String UNEXPECTED_ERROR = "error.unexpected";

    public static final String PROCESS_NOT_FOUND = "error.process.notFound";
    public static final String PROCESS_INSTANCE_ID_REQUIRED = "error.processInstanceId.required";
    public static final String PROCESS_IDENTIFIER_MISMATCH = "error.process.idMismatch";
    public static final String TERMS_VERSION_REQUIRED = "error.termsVersion.required";
    public static final String CONSENT_MUST_BE_TRUE = "error.consent.accepted";
    public static final String REQUEST_BODY_INVALID = "error.request.invalidJson";

    public static final String BPMN_FILE_REQUIRED = "error.bpmn.noFile";
    public static final String FILE_READ_FAILURE = "error.file.read";
    public static final String FILE_TYPE_NOT_SUPPORTED = "error.file.type";

    public static final String CARD_FRONT_REQUIRED = "error.card.front.required";
    public static final String CARD_FRONT_TOO_LARGE = "error.card.front.size";
    public static final String CARD_BACK_REQUIRED = "error.card.back.required";
    public static final String CARD_BACK_TOO_LARGE = "error.card.back.size";
    public static final String CARD_NATIONAL_CODE_MISMATCH = "error.card.nationalCode.mismatch";

    public static final String SELFIE_REQUIRED = "error.selfie.required";
    public static final String SELFIE_TOO_LARGE = "error.selfie.size";

    public static final String SIGNATURE_REQUIRED = "error.signature.required";
    public static final String SIGNATURE_TOO_LARGE = "error.signature.size";

    public static final String VIDEO_REQUIRED = "error.video.required";
    public static final String VIDEO_TOO_LARGE = "error.video.size";

    public static final String WORKFLOW_ACCEPT_CONSENT_FAILED = "error.workflow.acceptConsent.failed";
    public static final String WORKFLOW_ENGLISH_INFO_FAILED = "error.workflow.englishInfo.failed";
    public static final String WORKFLOW_CARD_UPLOAD_FAILED = "error.workflow.cardUpload.failed";
    public static final String WORKFLOW_VIDEO_UPLOAD_FAILED = "error.workflow.videoUpload.failed";
    public static final String WORKFLOW_SIGNATURE_UPLOAD_FAILED = "error.workflow.signatureUpload.failed";
    public static final String WORKFLOW_ID_UPLOAD_FAILED = "error.workflow.idUpload.failed";
    public static final String WORKFLOW_SELFIE_UPLOAD_FAILED = "error.workflow.selfieUpload.failed";
    public static final String WORKFLOW_SELFIE_VALIDATION_FAILED = "error.workflow.selfieValidation.failed";
    public static final String WORKFLOW_BOOKLET_VALIDATION_FAILED = "error.workflow.bookletValidation.failed";
    public static final String WORKFLOW_PROCESS_CANCEL_FAILED = "error.workflow.processCancel.failed";

    public static final String ID_PAGES_REQUIRED = "error.id.pages.required";
    public static final String ID_PAGES_LIMIT = "error.id.pages.limit";
    public static final String ID_PAGE_REQUIRED = "error.id.page.required";
    public static final String ID_PAGE_TOO_LARGE = "error.id.page.size";

    public static final String EMAIL_REQUIRED = "error.email.required";
    public static final String EMAIL_INVALID = "error.email.invalid";

    public static final String ENGLISH_FIRST_NAME_REQUIRED = "error.englishInfo.firstName.required";
    public static final String ENGLISH_LAST_NAME_REQUIRED = "error.englishInfo.lastName.required";
    public static final String TELEPHONE_REQUIRED = "error.englishInfo.telephone.required";

    public static final String ADDRESS_REQUIRED = "error.address.required";
    public static final String POSTAL_CODE_REQUIRED = "error.postalCode.required";
    public static final String POSTAL_CODE_INVALID = "error.postalCode.invalid";

    public static final String TRACKING_NUMBER_REQUIRED = "error.trackingNumber.required";

    public static final String KYC_NOT_STARTED = "error.kyc.notStarted";
    public static final String KYC_STATUS_QUERY_FAILED = "error.kyc.status.queryFailed";
    public static final String CARD_DESCRIPTORS_REQUIRED = "error.kyc.card.descriptorsRequired";
    public static final String ID_DESCRIPTORS_REQUIRED = "error.kyc.id.descriptorsRequired";
    public static final String ID_DESCRIPTOR_LIMIT = "error.kyc.id.descriptorLimit";
    public static final String ID_DESCRIPTOR_NULL = "error.kyc.id.descriptorNull";
    public static final String SELFIE_DESCRIPTOR_REQUIRED = "error.kyc.selfie.descriptorRequired";
    public static final String SIGNATURE_DESCRIPTOR_REQUIRED = "error.kyc.signature.descriptorRequired";
    public static final String VIDEO_DESCRIPTOR_REQUIRED = "error.kyc.video.descriptorRequired";
    public static final String CONSENT_NOT_ACCEPTED = "error.kyc.consent.notAccepted";

    public static final String DOCUMENT_NOT_FOUND = "error.document.notFound";

    public static final String STORAGE_DESCRIPTOR_REQUIRED = "error.storage.descriptor.required";
    public static final String STORAGE_DOCUMENT_TYPE_REQUIRED = "error.storage.documentType.required";
    public static final String STORAGE_DESCRIPTOR_DATA_REQUIRED = "error.storage.descriptor.dataRequired";
    public static final String STORAGE_UNAVAILABLE = "error.storage.unavailable";
}
