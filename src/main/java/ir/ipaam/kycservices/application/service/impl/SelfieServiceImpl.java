package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.EsbFaceDetection;
import ir.ipaam.kycservices.application.service.SelfieService;
import ir.ipaam.kycservices.application.service.dto.FaceDetectionData;
import ir.ipaam.kycservices.application.service.dto.SelfieUploadResult;
import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.common.validation.FileTypeValidator;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.SELFIE_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.SELFIE_TOO_LARGE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_TYPE_NOT_SUPPORTED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.WORKFLOW_SELFIE_VALIDATION_FAILED;

@Slf4j
@Service
@RequiredArgsConstructor
public class SelfieServiceImpl implements SelfieService {

    public static final long MAX_SELFIE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_SELFIE_UPLOADED = "SELFIE_UPLOADED";

    private static final double FACE_CONFIDENCE_THRESHOLD = 0.9d;

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;
    private final EsbFaceDetection faceDetection;

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

        MediaType contentType = resolveContentType(selfie);
        FaceDetectionData detectionData = faceDetection.detect(
                selfieBytes,
                selfie.getOriginalFilename(),
                contentType,
                normalizedProcessId);

        if (detectionData.highestConfidence() == null
                || detectionData.highestConfidence() < FACE_CONFIDENCE_THRESHOLD) {
            log.warn("Selfie validation failed for process {} with confidence {}",
                    normalizedProcessId, detectionData.highestConfidence());
            throw new IllegalArgumentException(WORKFLOW_SELFIE_VALIDATION_FAILED);
        }

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(selfieBytes, "selfie_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadSelfieCommand(normalizedProcessId, descriptor));

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        Map<String, Object> body = new HashMap<>();
        body.put("processInstanceId", normalizedProcessId);
        body.put("selfieSize", selfieBytes.length);
        body.put("status", "SELFIE_RECEIVED");
        body.put("faceConfidence", detectionData.highestConfidence());
        body.put("faceTrackId", detectionData.trackId());
        return SelfieUploadResult.of(HttpStatus.ACCEPTED, body);
    }

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            log.warn("Invalid selfie content type '{}', defaulting to application/octet-stream", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
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
        FileTypeValidator.ensureAllowedType(
                file,
                FileTypeValidator.IMAGE_CONTENT_TYPES,
                FileTypeValidator.IMAGE_EXTENSIONS,
                FILE_TYPE_NOT_SUPPORTED);
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
