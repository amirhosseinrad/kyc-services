package ir.ipaam.kycservices.application.service;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.dto.CardDocumentUploadResult;
import ir.ipaam.kycservices.common.image.ImageCompressionHelper;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
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
public class CardDocumentService {

    public static final long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_CARD_DOCUMENTS_UPLOADED = "CARD_DOCUMENTS_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;

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
