package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.EsbLivenessDetection;
import ir.ipaam.kycservices.application.service.VideoService;
import ir.ipaam.kycservices.application.service.dto.LivenessCheckData;
import ir.ipaam.kycservices.application.service.dto.VideoUploadRequest;
import ir.ipaam.kycservices.application.service.dto.VideoUploadResponse;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.common.validation.FileTypeValidator;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.FILE_TYPE_NOT_SUPPORTED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.SELFIE_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.SELFIE_TOO_LARGE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.VIDEO_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.VIDEO_TOO_LARGE;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.WORKFLOW_VIDEO_UPLOAD_FAILED;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private static final long MAX_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final double LIVENESS_THRESHOLD = 0.8d;
    private static final String STEP_VIDEO_UPLOADED = "VIDEO_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;
    private final EsbLivenessDetection livenessDetection;

    @Override
    public VideoUploadResponse uploadVideo(VideoUploadRequest request) {
        MultipartFile video = request.video();
        MultipartFile image = request.image();
        validateFile(
                video,
                VIDEO_REQUIRED,
                VIDEO_TOO_LARGE,
                MAX_VIDEO_SIZE_BYTES,
                FileTypeValidator.VIDEO_CONTENT_TYPES,
                FileTypeValidator.VIDEO_EXTENSIONS);
        validateFile(
                image,
                SELFIE_REQUIRED,
                SELFIE_TOO_LARGE,
                MAX_IMAGE_SIZE_BYTES,
                FileTypeValidator.IMAGE_CONTENT_TYPES,
                FileTypeValidator.IMAGE_EXTENSIONS);
        String normalizedProcessId = normalizeProcessInstanceId(request.processInstanceId());

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_VIDEO_UPLOADED)) {
            return new VideoUploadResponse(
                    normalizedProcessId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "VIDEO_ALREADY_UPLOADED"
            );
        }

        byte[] videoBytes = readFile(video);
        byte[] imageBytes = readFile(image);

        MediaType videoContentType = resolveContentType(video);
        MediaType imageContentType = resolveContentType(image);
        LivenessCheckData livenessData = livenessDetection.check(
                videoBytes,
                video.getOriginalFilename(),
                videoContentType,
                imageBytes,
                image.getOriginalFilename(),
                imageContentType,
                normalizedProcessId);

        boolean match = isMatch(livenessData);

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(videoBytes, "video_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadVideoCommand(normalizedProcessId, descriptor));

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard, match, livenessData);

        return new VideoUploadResponse(
                normalizedProcessId,
                videoBytes.length,
                match,
                livenessData.livenessScore(),
                livenessData.isReal(),
                livenessData.trackId(),
                livenessData.framesCount(),
                "VIDEO_RECEIVED"
        );
    }

    private void publishWorkflowUpdate(String processInstanceId,
                                       Boolean hasNewCard,
                                       boolean match,
                                       LivenessCheckData livenessData) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "VIDEO_UPLOADED");
        variables.put("match", match);
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        if (livenessData.isReal() != null) {
            variables.put("isReal", livenessData.isReal());
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("video-uploaded")
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();
    }

    private boolean isMatch(LivenessCheckData livenessData) {
        if (livenessData == null) {
            throw new IllegalArgumentException(WORKFLOW_VIDEO_UPLOAD_FAILED);
        }
        Double livenessScore = livenessData.livenessScore();
        return livenessScore != null
                && livenessScore >= LIVENESS_THRESHOLD;
    }

    private void validateFile(MultipartFile file,
                              String requiredKey,
                              String sizeKey,
                              long maxSize,
                              Set<String> allowedContentTypes,
                              Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(requiredKey);
        }
        FileTypeValidator.ensureAllowedType(file, allowedContentTypes, allowedExtensions, FILE_TYPE_NOT_SUPPORTED);
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

    private MediaType resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            log.warn("Invalid video content type '{}', defaulting to application/octet-stream", contentType);
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FileProcessingException(FILE_READ_FAILURE, e);
        }
    }
}
