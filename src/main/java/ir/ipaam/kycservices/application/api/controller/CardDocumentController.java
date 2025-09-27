package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.common.image.ImageCompressionHelper;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
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
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
public class CardDocumentController {

    public static final long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final ZeebeClient zeebeClient;

    @PostMapping(path = "/card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        validateFile(frontImage, CARD_FRONT_REQUIRED);
        validateFile(backImage, CARD_BACK_REQUIRED);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

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
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
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
