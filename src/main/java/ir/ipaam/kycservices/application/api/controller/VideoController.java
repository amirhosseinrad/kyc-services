package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.InquiryTokenService;
import ir.ipaam.kycservices.domain.command.UploadVideoCommand;
import ir.ipaam.kycservices.domain.exception.InquiryTokenException;
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
import java.util.Map;

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
public class VideoController {

    public static final long MAX_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final InquiryTokenService inquiryTokenService;

    @PostMapping(path = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestPart("video") MultipartFile video,
            @RequestPart("processInstanceId") String processInstanceId) {
        validateFile(video, VIDEO_REQUIRED, VIDEO_TOO_LARGE, MAX_VIDEO_SIZE_BYTES);
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        if (kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId).isEmpty()) {
            log.warn("Process instance with id {} not found", normalizedProcessId);
            throw new ResourceNotFoundException(PROCESS_NOT_FOUND);
        }

        byte[] videoBytes = readFile(video);

        String inquiryToken = inquiryTokenService.generateToken(normalizedProcessId)
                .orElseThrow(() -> new InquiryTokenException(INQUIRY_TOKEN_FAILED));

        DocumentPayloadDescriptor descriptor =
                new DocumentPayloadDescriptor(videoBytes, "video_" + normalizedProcessId);

        commandGateway.sendAndWait(new UploadVideoCommand(normalizedProcessId, descriptor, inquiryToken));

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "videoSize", videoBytes.length,
                "status", "VIDEO_RECEIVED"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
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
