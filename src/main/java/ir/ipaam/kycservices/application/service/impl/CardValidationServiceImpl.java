package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.CardValidationService;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import ir.ipaam.kycservices.application.service.dto.CardOcrBackData;
import ir.ipaam.kycservices.application.service.dto.CardOcrFrontData;
import ir.ipaam.kycservices.common.image.ImageCompressionHelper;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.CustomerRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.CARD_BACK_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.CARD_BACK_TOO_LARGE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.CARD_FRONT_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.CARD_FRONT_TOO_LARGE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardValidationServiceImpl {

    public static final long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_CARD_DOCUMENTS_UPLOADED = "CARD_DOCUMENTS_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CustomerRepository customerRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;
    private final CardValidationService cardValidationService;

    public CardDocumentUploadResult uploadCardDocuments(MultipartFile frontImage,
                                                        MultipartFile backImage,
                                                        String processInstanceId) {
        validateFile(frontImage, CARD_FRONT_REQUIRED);
        validateFile(backImage, CARD_BACK_REQUIRED);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_CARD_DOCUMENTS_UPLOADED)) {
            return CardDocumentUploadResult.of(HttpStatus.CONFLICT, Map.of(
                    "processInstanceId", normalizedProcessId,
                    "status", "CARD_DOCUMENTS_ALREADY_UPLOADED"
            ));
        }

        byte[] frontBytes = ensureWithinLimit(readFile(frontImage), CARD_FRONT_TOO_LARGE);
        byte[] backBytes = ensureWithinLimit(readFile(backImage), CARD_BACK_TOO_LARGE);

        CardOcrFrontData frontData = null;
        CardOcrBackData backData = null;
        try {
            frontData = cardValidationService.extractFront(frontBytes, frontImage.getOriginalFilename());
            backData = cardValidationService.extractBack(backBytes, backImage.getOriginalFilename());
        } catch (RuntimeException ex) {
            log.error("Failed to extract OCR data for process {}", normalizedProcessId, ex);
            throw ex;
        }

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

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "frontImageSize", frontBytes.length,
                "backImageSize", backBytes.length,
                "status", "CARD_DOCUMENTS_RECEIVED"
        );
        return CardDocumentUploadResult.of(HttpStatus.ACCEPTED, body);
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
            customer.setFirstName(frontData.firstName());
            customer.setLastName(frontData.lastName());
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
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
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
