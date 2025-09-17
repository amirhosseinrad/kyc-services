package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.domain.command.UploadSelfieCommand;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
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

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
public class SelfieController {

    public static final long MAX_SELFIE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @PostMapping(path = "/selfie", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSelfie(
            @RequestPart("selfie") MultipartFile selfie,
            @RequestPart("processInstanceId") String processInstanceId) {
        try {
            validateFile(selfie, "selfie");
            String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

            byte[] selfieBytes = selfie.getBytes();

            if (kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId).isEmpty()) {
                log.warn("Process instance with id {} not found", normalizedProcessId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Process instance not found"));
            }

            DocumentPayloadDescriptor descriptor =
                    new DocumentPayloadDescriptor(selfieBytes, "selfie_" + normalizedProcessId);

            commandGateway.sendAndWait(new UploadSelfieCommand(normalizedProcessId, descriptor));

            Map<String, Object> body = Map.of(
                    "processInstanceId", normalizedProcessId,
                    "selfieSize", selfieBytes.length,
                    "status", "SELFIE_RECEIVED"
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid selfie upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to read uploaded selfie", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unable to read uploaded file"));
        } catch (CommandExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                log.warn("Command execution rejected: {}", illegalArgumentException.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", illegalArgumentException.getMessage()));
            }
            log.error("Command execution failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process selfie"));
        } catch (Exception e) {
            log.error("Unexpected error while processing selfie upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process selfie"));
        }
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        if (file.getSize() > MAX_SELFIE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds maximum size of " + MAX_SELFIE_SIZE_BYTES + " bytes");
        }
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }
}
