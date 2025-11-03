package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.SelfieService;
import ir.ipaam.kycservices.application.service.dto.SelfieUploadResult;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
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

import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SELFIE_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.SELFIE_TOO_LARGE;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfieServiceImpl implements SelfieService {

    public static final long MAX_SELFIE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_SELFIE_UPLOADED = "SELFIE_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;

    @Override
    public SelfieUploadResult uploadSelfie(MultipartFile selfie, String processInstanceId) {
        validateFile(selfie, SELFIE_REQUIRED, SELFIE_TOO_LARGE, MAX_SELFIE_SIZE_BYTES);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_SELFIE_UPLOADED)) {
            return SelfieUploadResult.of(HttpStatus.CONFLICT, Map.of(
                    "processInstanceId", normalizedProcessId,
                    "status", "SELFIE_ALREADY_UPLOADED"
            ));
        }

        byte[] selfieBytes = readFile(selfie);

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(selfieBytes, "selfie_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadSelfieCommand(normalizedProcessId, descriptor));

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "selfieSize", selfieBytes.length,
                "status", "SELFIE_RECEIVED"
        );
        return SelfieUploadResult.of(HttpStatus.ACCEPTED, body);
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "SELFIE_UPLOADED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("selfie-uploaded")
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
