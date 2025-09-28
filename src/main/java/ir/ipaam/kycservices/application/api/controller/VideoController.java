package ir.ipaam.kycservices.application.api.controller;

import io.camunda.zeebe.client.ZeebeClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.INQUIRY_TOKEN_FAILED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.VIDEO_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.VIDEO_TOO_LARGE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Video Upload", description = "Receive short selfie videos required for liveness validation.")
public class VideoController {

    public static final long MAX_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private static final String STEP_VIDEO_UPLOADED = "VIDEO_UPLOADED";
    private static final String VIDEO_PENDING_STATUS = "VIDEO_PENDING";
    private static final String VIDEO_PENDING_MESSAGE =
            "Video verification is temporarily unavailable. Please try again later.";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final InquiryTokenService inquiryTokenService;
    private final ZeebeClient zeebeClient;

    @Operation(
            summary = "Upload a verification video",
            description = "Accepts a selfie video up to 10 MB, generates an inquiry token, stores the payload, and "
                    + "triggers the VIDEO_UPLOADED workflow event."
    )
    @PostMapping(path = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestPart("video") MultipartFile video,
            @RequestPart("processInstanceId") String processInstanceId) {
        validateFile(video, VIDEO_REQUIRED, VIDEO_TOO_LARGE, MAX_VIDEO_SIZE_BYTES);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_VIDEO_UPLOADED)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "processInstanceId", normalizedProcessId,
                    "status", "VIDEO_ALREADY_UPLOADED"
            ));
        }

        byte[] videoBytes = readFile(video);

        Optional<String> inquiryToken;
        try {
            inquiryToken = inquiryTokenService.generateToken(normalizedProcessId);
        } catch (RuntimeException ex) {
            if (ex instanceof InquiryTokenException inquiryEx) {
                log.warn("Unable to generate inquiry token for video step of process {}", normalizedProcessId, inquiryEx);
                return handleTokenFailure(processInstance, normalizedProcessId, inquiryEx.getMessage());
            }
            throw ex;
        }

        if (inquiryToken.isEmpty()) {
            log.warn("Inquiry token service returned empty token for video step of process {}", normalizedProcessId);
            return handleTokenFailure(processInstance, normalizedProcessId, null);
        }

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(videoBytes, "video_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadVideoCommand(normalizedProcessId, descriptor, inquiryToken.get()));

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "videoSize", videoBytes.length,
                "status", "VIDEO_RECEIVED"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private ResponseEntity<Map<String, Object>> handleTokenFailure(
            ProcessInstance processInstance,
            String processInstanceId,
            String errorCause) {
        recordFailedStep(processInstance, STEP_VIDEO_UPLOADED, errorCause);
        Map<String, Object> body = Map.of(
                "processInstanceId", processInstanceId,
                "status", VIDEO_PENDING_STATUS,
                "message", VIDEO_PENDING_MESSAGE
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private void recordFailedStep(ProcessInstance processInstance, String stepName, String errorCause) {
        if (processInstance == null) {
            return;
        }

        processInstance.setStatus(stepName + "_FAILED");

        StepStatus stepStatus = new StepStatus();
        stepStatus.setProcess(processInstance);
        stepStatus.setStepName(stepName);
        stepStatus.setTimestamp(LocalDateTime.now());
        stepStatus.setState(StepStatus.State.FAILED);
        String cause = (errorCause != null && !errorCause.isBlank()) ? errorCause : INQUIRY_TOKEN_FAILED;
        stepStatus.setErrorCause(cause);

        if (processInstance.getStatuses() == null) {
            processInstance.setStatuses(new java.util.ArrayList<>());
        }
        processInstance.getStatuses().add(stepStatus);
        kycProcessInstanceRepository.save(processInstance);
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "VIDEO_UPLOADED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("video-uploaded")
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
