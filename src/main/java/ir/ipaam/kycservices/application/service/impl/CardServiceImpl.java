package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.dto.CardStatusRequest;
import ir.ipaam.kycservices.application.api.dto.RecordTrackingNumberRequest;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.CardService;
import ir.ipaam.kycservices.application.service.EsbNationalCardValidation;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadRequest;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResponse;
import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;
import ir.ipaam.kycservices.application.service.dto.CardStatusResponse;
import ir.ipaam.kycservices.application.service.dto.CardTrackingResponse;
import ir.ipaam.kycservices.common.image.ImageCompressionHelper;
import ir.ipaam.kycservices.common.validation.FileTypeValidator;
import ir.ipaam.kycservices.domain.command.RecordTrackingNumberCommand;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.Customer;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import ir.ipaam.kycservices.infrastructure.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.CARD_BACK_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.CARD_BACK_TOO_LARGE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.CARD_FRONT_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.CARD_FRONT_TOO_LARGE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.CARD_NATIONAL_CODE_MISMATCH;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_TYPE_NOT_SUPPORTED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.TRACKING_NUMBER_REQUIRED;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private static final long MAX_IMAGE_SIZE_BYTES = CardService.MAX_IMAGE_SIZE_BYTES;

    private static final String STEP_CARD_DOCUMENTS_UPLOADED = "CARD_DOCUMENTS_UPLOADED";
    private static final String STEP_CARD_STATUS_RECORDED = "CARD_STATUS_RECORDED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;
    private final EsbNationalCardValidation esbNationalCardValidation;
    private final MinioStorageService minioStorageService;

    @Override
    public CardDocumentUploadResponse uploadCardDocuments(CardDocumentUploadRequest request) {
        MultipartFile frontImage = request.frontImage();
        MultipartFile backImage = request.backImage();
        validateFile(frontImage, CARD_FRONT_REQUIRED);
        validateFile(backImage, CARD_BACK_REQUIRED);
        String normalizedProcessId = normalizeProcessInstanceId(request.processInstanceId());

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_CARD_DOCUMENTS_UPLOADED)) {
            return new CardDocumentUploadResponse(
                    normalizedProcessId,
                    null,
                    null,
                    "CARD_DOCUMENTS_ALREADY_UPLOADED"
            );
        }

        minioStorageService.assertAvailable();

        byte[] frontBytes = ensureWithinLimit(readFile(frontImage), CARD_FRONT_TOO_LARGE);
        byte[] backBytes = ensureWithinLimit(readFile(backImage), CARD_BACK_TOO_LARGE);

        CardOcrFrontData frontData = null;
        CardOcrBackData backData = null;
        try {
            frontData = esbNationalCardValidation.extractFront(frontBytes, frontImage.getOriginalFilename());
            backData = esbNationalCardValidation.extractBack(backBytes, backImage.getOriginalFilename());
        } catch (RuntimeException ex) {
            log.error("Failed to extract OCR data for process {}", normalizedProcessId, ex);
            throw ex;
        }

        ensureOcrMatchesCustomer(processInstance, frontData);

        updateCustomerWithOcr(processInstance, frontData, backData);

        UploadCardDocumentsCommand command = new UploadCardDocumentsCommand(
                normalizedProcessId,
                new DocumentPayloadDescriptor(frontBytes, "frontImage_" + normalizedProcessId),
                new DocumentPayloadDescriptor(backBytes, "backImage_" + normalizedProcessId)
        );

        commandGateway.sendAndWait(command);

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        return new CardDocumentUploadResponse(
                normalizedProcessId,
                frontBytes.length,
                backBytes.length,
                "CARD_DOCUMENTS_RECEIVED"
        );
    }

    @Override
    public CardStatusResponse updateCardStatus(CardStatusRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        boolean hasNewNationalCard = Boolean.TRUE.equals(request.hasNewNationalCard());

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException(PROCESS_NOT_FOUND));

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                processInstanceId,
                STEP_CARD_STATUS_RECORDED)) {
            Boolean recordedCardState = Optional.ofNullable(processInstance.getCustomer())
                    .map(Customer::getHasNewNationalCard)
                    .orElse(null);
            return new CardStatusResponse(
                    processInstanceId,
                    recordedCardState,
                    "CARD_STATUS_ALREADY_RECORDED"
            );
        }

        Customer customer = processInstance.getCustomer();
        if (customer == null) {
            log.warn("Process {} does not have an associated customer when recording card status", processInstanceId);
            throw new ResourceNotFoundException(PROCESS_NOT_FOUND);
        }

        parseProcessInstanceKey(processInstanceId);

        customer.setHasNewNationalCard(hasNewNationalCard);
        customerRepository.save(customer);

        commandGateway.sendAndWait(new UpdateKycStatusCommand(
                processInstanceId,
                STEP_CARD_STATUS_RECORDED,
                STEP_CARD_STATUS_RECORDED,
                "PASSED"
        ));
        zeebeClient.newPublishMessageCommand()
                .messageName("card-status-recorded")
                .correlationKey(processInstanceId)
                .variables(Map.of(
                        "card", hasNewNationalCard,
                        "processInstanceId", processInstanceId,
                        "kycStatus", STEP_CARD_STATUS_RECORDED
                ))
                .send()
                .join();

        return new CardStatusResponse(
                processInstanceId,
                hasNewNationalCard,
                STEP_CARD_STATUS_RECORDED
        );
    }

    @Override
    public CardTrackingResponse recordTrackingNumber(RecordTrackingNumberRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.getProcessInstanceNumber());
        String trackingNumber = normalizeTrackingNumber(request.getTrackingNumber());

        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("trackingNumber", trackingNumber);
        variables.put("status", "RECORD_NATIONAL_CARD_TRACKING_NUMBER");

        commandGateway.sendAndWait(new RecordTrackingNumberCommand(trackingNumber, processInstanceId));
        zeebeClient.newPublishMessageCommand()
                .messageName("save-national-card-tracking-number")
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();

        return new CardTrackingResponse(
                processInstanceId,
                trackingNumber,
                "RECORD_NATIONAL_CARD_TRACKING_NUMBER"
        );
    }

    private void ensureOcrMatchesCustomer(ProcessInstance processInstance, CardOcrFrontData frontData) {
        if (processInstance.getCustomer() == null || frontData == null || !StringUtils.hasText(frontData.nin())) {
            return;
        }

        String ocrNationalCode = frontData.nin().trim();
        String customerNationalCode = processInstance.getCustomer().getNationalCode();
        if (StringUtils.hasText(customerNationalCode)
                && !ocrNationalCode.equals(customerNationalCode.trim())) {
            log.warn("OCR national code {} does not match persisted national code {} for process {}",
                    ocrNationalCode, customerNationalCode, processInstance.getCamundaInstanceId());
            throw new IllegalArgumentException(CARD_NATIONAL_CODE_MISMATCH);
        }
    }

    private void updateCustomerWithOcr(ProcessInstance processInstance,
                                       CardOcrFrontData frontData,
                                       CardOcrBackData backData) {
        if (processInstance.getCustomer() == null) {
            log.warn("Process instance {} has no associated customer to update with OCR data", processInstance.getCamundaInstanceId());
            return;
        }

        var customer = processInstance.getCustomer();

        if (frontData != null) {
            if (frontData.nin() != null) {
                customer.setNationalCode(frontData.nin());
            }
            customer.setFirstName_fa(frontData.firstName());
            customer.setLastName_fa(frontData.lastName());
            customer.setFatherName(frontData.fatherName());
            customer.setBirthDate(parseDate(frontData.dateOfBirth()));
            customer.setCardExpirationDate(parseDate(frontData.dateOfExpiration()));
            customer.setCardOcrFrontTrackId(frontData.trackId());
        }

        if (backData != null) {
            customer.setCardSerialNumber(backData.serialNumber());
            customer.setCardBarcode(backData.barcode());
            customer.setHasNewNationalCard(backData.ocrSuccess());
            customer.setCardOcrBackTrackId(backData.trackId());
        }

        customerRepository.save(customer);
    }

    private java.time.LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            log.warn("Unable to parse date value '{}' from OCR response", value, ex);
            return null;
        }
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "CARD_DOCUMENTS_UPLOADED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("card-documents-uploaded")
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();
    }

    private void validateFile(MultipartFile file, String requiredKey) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(requiredKey);
        }
        FileTypeValidator.ensureAllowedType(
                file,
                FileTypeValidator.IMAGE_CONTENT_TYPES,
                FileTypeValidator.IMAGE_EXTENSIONS,
                FILE_TYPE_NOT_SUPPORTED);
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private long parseProcessInstanceKey(String processInstanceId) {
        try {
            return Long.parseLong(processInstanceId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("processInstanceId must be a numeric value", ex);
        }
    }

    private String normalizeTrackingNumber(String trackingNumber) {
        if (!StringUtils.hasText(trackingNumber)) {
            throw new IllegalArgumentException(TRACKING_NUMBER_REQUIRED);
        }
        return trackingNumber.trim();
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FileProcessingException(FILE_READ_FAILURE, e);
        }
    }

    private byte[] ensureWithinLimit(byte[] data, String sizeKey) {
        if (data.length > MAX_IMAGE_SIZE_BYTES) {
            try {
                data = ImageCompressionHelper.reduceToMaxSize(data, MAX_IMAGE_SIZE_BYTES);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(sizeKey);
            }
        }

        if (data.length > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException(sizeKey);
        }

        return data;
    }
}
