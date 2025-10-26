package ir.ipaam.kycservices.application.service;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.dto.BookletValidationData;
import ir.ipaam.kycservices.domain.command.UploadIdPagesCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ir.ipaam.kycservices.common.ErrorMessageKeys.FILE_READ_FAILURE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGE_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGE_TOO_LARGE;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGES_LIMIT;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.ID_PAGES_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.common.ErrorMessageKeys.PROCESS_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookletService {

    public static final long MAX_PAGE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final String STEP_ID_PAGES_UPLOADED = "ID_PAGES_UPLOADED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final ZeebeClient zeebeClient;
    private final BookletValidationClient bookletValidationClient;

    public ResponseEntity<Map<String, Object>> uploadBookletPages(List<MultipartFile> pages, String processInstanceId) {
        List<MultipartFile> normalizedPages = pages == null ? List.of() : pages;
        if (normalizedPages.isEmpty()) {
            throw new IllegalArgumentException(ID_PAGES_REQUIRED);
        }
        if (normalizedPages.size() > 4) {
            throw new IllegalArgumentException(ID_PAGES_LIMIT);
        }

        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);

        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        if (kycStepStatusRepository.existsByProcess_CamundaInstanceIdAndStepName(
                normalizedProcessId,
                STEP_ID_PAGES_UPLOADED)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "processInstanceId", normalizedProcessId,
                    "status", "ID_PAGES_ALREADY_UPLOADED"
            ));
        }

        List<DocumentPayloadDescriptor> descriptors = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        List<BookletValidationData> validationResults = new ArrayList<>();
        for (int i = 0; i < normalizedPages.size(); i++) {
            MultipartFile page = normalizedPages.get(i);
            validateFile(page, ID_PAGE_REQUIRED, ID_PAGE_TOO_LARGE, MAX_PAGE_SIZE_BYTES);
            byte[] pageBytes = readFile(page);
            sizes.add(pageBytes.length);
            String filename = resolveFilename(page, i);
            descriptors.add(new DocumentPayloadDescriptor(pageBytes, filename));
            MediaType contentType = resolveContentType(page, filename);
            BookletValidationData validationData = bookletValidationClient.validate(pageBytes, filename, contentType);
            validationResults.add(validationData);
        }

        UploadIdPagesCommand command = new UploadIdPagesCommand(normalizedProcessId, List.copyOf(descriptors));
        commandGateway.sendAndWait(command);

        Boolean hasNewCard = null;
        if (processInstance.getCustomer() != null) {
            hasNewCard = processInstance.getCustomer().getHasNewNationalCard();
        }

        publishWorkflowUpdate(normalizedProcessId, hasNewCard);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processInstanceId", normalizedProcessId);
        body.put("pageCount", descriptors.size());
        body.put("pageSizes", List.copyOf(sizes));
        body.put("validationResults", validationResults.stream()
                .map(BookletService::toValidationResult)
                .toList());
        body.put("status", "ID_PAGES_RECEIVED");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private static Map<String, Object> toValidationResult(BookletValidationData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("trackId", data.trackId());
        map.put("type", data.type());
        map.put("rotation", data.rotation());
        return map;
    }

    private void publishWorkflowUpdate(String processInstanceId, Boolean hasNewCard) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("kycStatus", "ID_PAGES_UPLOADED");
        if (hasNewCard != null) {
            variables.put("card", hasNewCard);
        }
        zeebeClient.newPublishMessageCommand()
                .messageName("id-pages-uploaded")
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

    private String resolveFilename(MultipartFile file, int index) {
        String original = file.getOriginalFilename();
        if (StringUtils.hasText(original)) {
            return original;
        }
        return "page-" + (index + 1);
    }

    private MediaType resolveContentType(MultipartFile file, String filename) {
        String provided = file.getContentType();
        if (StringUtils.hasText(provided)) {
            try {
                return MediaType.parseMediaType(provided);
            } catch (InvalidMediaTypeException ex) {
                log.debug("Ignoring invalid content type '{}' for booklet page {}", provided, filename, ex);
            }
        }
        return MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
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
