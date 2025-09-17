package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
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
@RequestMapping("/kyc/documents")
public class CardDocumentController {

    public static final long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final CommandGateway commandGateway;

    @PostMapping(path = "/card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        try {
            validateFile(frontImage, "frontImage");
            validateFile(backImage, "backImage");
            String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

            byte[] frontBytes = frontImage.getBytes();
            byte[] backBytes = backImage.getBytes();

            UploadCardDocumentsCommand command = new UploadCardDocumentsCommand(
                    normalizedProcessId,
                    frontBytes,
                    backBytes,
                    frontImage.getOriginalFilename(),
                    backImage.getOriginalFilename(),
                    frontImage.getContentType(),
                    backImage.getContentType()
            );

            commandGateway.sendAndWait(command);

            Map<String, Object> body = Map.of(
                    "processInstanceId", normalizedProcessId,
                    "frontImageSize", frontBytes.length,
                    "backImageSize", backBytes.length,
                    "status", "CARD_DOCUMENTS_RECEIVED"
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid card document upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to read uploaded card documents", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Unable to read uploaded files"));
        } catch (CommandExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException illegalArgumentException) {
                log.warn("Command execution rejected: {}", illegalArgumentException.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", illegalArgumentException.getMessage()));
            }
            log.error("Command execution failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process card documents"));
        } catch (Exception e) {
            log.error("Unexpected error while processing card document upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process card documents"));
        }
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum size of " + MAX_IMAGE_SIZE_BYTES + " bytes");
        }
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }
}
