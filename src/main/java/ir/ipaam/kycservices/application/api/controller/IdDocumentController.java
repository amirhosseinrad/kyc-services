package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/documents")
public class IdDocumentController {

    public static final long MAX_PAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @PostMapping(path = "/id", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadIdPages(
            @RequestPart("pages") List<MultipartFile> pages,
            @RequestPart("processInstanceId") String processInstanceId) {
        try {
            List<MultipartFile> normalizedPages = pages == null ? List.of() : pages;
            if (normalizedPages.isEmpty()) {
                throw new IllegalArgumentException("At least one page must be provided");
            }
            if (normalizedPages.size() > 4) {
                throw new IllegalArgumentException("No more than four pages may be provided");
            }

            String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

            if (kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId).isEmpty()) {
                log.warn("Process instance with id {} not found", normalizedProcessId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Process instance not found"));
            }

            List<DocumentPayloadDescriptor> descriptors = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            for (int i = 0; i < normalizedPages.size(); i++) {
                MultipartFile page = normalizedPages.get(i);
                validateFile(page, "pages[" + i + "]");
                byte[] pageBytes = page.getBytes();
                sizes.add(pageBytes.length);
                String filename = resolveFilename(page, i);
                descriptors.add(new DocumentPayloadDescriptor(pageBytes, filename));
            }

            UploadIdPagesCommand command = new UploadIdPagesCommand(normalizedProcessId, List.copyOf(descriptors));
            commandGateway.sendAndWait(command);

            Map<String, Object> body = Map.of(
                    "processInstanceId", normalizedProcessId,
                    "pageCount", descriptors.size(),
                    "pageSizes", List.copyOf(sizes),
                    "status", "ID_PAGES_RECEIVED"
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid ID pages upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to read uploaded ID pages", e);
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
                    .body(Map.of("error", "Failed to process ID pages"));
        } catch (Exception e) {
            log.error("Unexpected error while processing ID pages upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process ID pages"));
        }
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        if (file.getSize() > MAX_PAGE_SIZE_BYTES) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum size of " + MAX_PAGE_SIZE_BYTES + " bytes");
        }
    }

    private String resolveFilename(MultipartFile file, int index) {
        String original = file.getOriginalFilename();
        if (StringUtils.hasText(original)) {
            return original;
        }
        return "page-" + (index + 1);
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }
}
