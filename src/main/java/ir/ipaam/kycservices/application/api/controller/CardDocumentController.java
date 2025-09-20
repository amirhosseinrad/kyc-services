package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.UploadCardDocumentsCommand;
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

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
public class CardDocumentController {

    public static final long MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @PostMapping(path = "/card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCardDocuments(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage") MultipartFile backImage,
            @RequestPart("processInstanceId") String processInstanceId) {
        validateFile(frontImage, "frontImage");
        validateFile(backImage, "backImage");
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        if (kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId).isEmpty()) {
            log.warn("Process instance with id {} not found", normalizedProcessId);
            throw new ResourceNotFoundException("Process instance not found");
        }

        byte[] frontBytes = readFile(frontImage);
        byte[] backBytes = readFile(backImage);

        UploadCardDocumentsCommand command = new UploadCardDocumentsCommand(
                normalizedProcessId,
                new DocumentPayloadDescriptor(frontBytes, "frontImage_" + normalizedProcessId),
                new DocumentPayloadDescriptor(backBytes, "backImage_" + normalizedProcessId)
        );

        commandGateway.sendAndWait(command);

        Map<String, Object> body = Map.of(
                "processInstanceId", normalizedProcessId,
                "frontImageSize", frontBytes.length,
                "backImageSize", backBytes.length,
                "status", "CARD_DOCUMENTS_RECEIVED"
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
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

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new FileProcessingException("Unable to read uploaded files", e);
        }
    }
}
