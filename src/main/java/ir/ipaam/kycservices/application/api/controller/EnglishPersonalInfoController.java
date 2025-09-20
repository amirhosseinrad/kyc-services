package ir.ipaam.kycservices.application.api.controller;

import ir.ipaam.kycservices.application.api.dto.EnglishPersonalInfoRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.domain.command.ProvideEnglishPersonalInfoCommand;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc/english-info")
@Validated
public class EnglishPersonalInfoController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\\s]+@[^@\\\s]+\\.[^@\\\s]+$");

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> provideEnglishPersonalInfo(@Valid @RequestBody EnglishPersonalInfoRequest request) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        if (kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId).isEmpty()) {
            log.warn("Process instance with id {} not found", processInstanceId);
            throw new ResourceNotFoundException("Process instance not found");
        }

        String firstNameEn = normalizeRequiredText(request.firstNameEn(), "firstNameEn");
        String lastNameEn = normalizeRequiredText(request.lastNameEn(), "lastNameEn");
        String email = normalizeEmail(request.email());
        String telephone = normalizeRequiredText(request.telephone(), "telephone");

        ProvideEnglishPersonalInfoCommand command = new ProvideEnglishPersonalInfoCommand(
                processInstanceId,
                firstNameEn,
                lastNameEn,
                email,
                telephone
        );

        commandGateway.sendAndWait(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "processInstanceId", processInstanceId,
                "firstNameEn", firstNameEn,
                "lastNameEn", lastNameEn,
                "email", email,
                "telephone", telephone,
                "status", "ENGLISH_PERSONAL_INFO_PROVIDED"
        ));
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException("processInstanceId must be provided");
        }
        return processInstanceId.trim();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must be provided");
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeRequiredText(email, "email");
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email must be a valid email address");
        }
        return normalized;
    }
}
