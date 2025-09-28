package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.UploadSignatureCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
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

import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SIGNATURE_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SIGNATURE_TOO_LARGE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Signature Upload", description = "Collect handwritten signatures for contract fulfillment.")
public class SignatureController {

    public static final long MAX_SIGNATURE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_SIGNATURE_UPLOADED = "SIGNATURE_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;

    @Operation(
            summary = "Upload a handwritten signature",
            description = "Accepts a scanned signature image for the active process, stores it, and emits a "
                    + "signature-uploaded workflow message. Rejects missing or oversized files."
    )
    @PostMapping(path = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSignature(
            @RequestPart("signature") MultipartFile signature,
            @RequestPart("processInstanceId") String processInstanceId) {
        validateFile(signature, SIGNATURE_REQUIRED, SIGNATURE_TOO_LARGE, MAX_SIGNATURE_SIZE_BYTES);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_SIGNATURE_UPLOADED)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "processInstanceId", normalizedProcessId,
                    "status", "SIGNATURE_ALREADY_UPLOADED"
            ));
        }

        byte[] signatureBytes = readFile(signature);

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(signatureBytes, "signature_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadSignatureCommand(normalizedProcessId, descriptor));

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "signatureSize", signatureBytes.length,
                "status", "SIGNATURE_RECEIVED"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "SIGNATURE_UPLOADED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("signature-uploaded")
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();
    }

    private void validateFile(MultipartFile file, String requiredKey, String sizeKey, long maxSize) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(requiredKey);
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(sizeKey);
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
}
