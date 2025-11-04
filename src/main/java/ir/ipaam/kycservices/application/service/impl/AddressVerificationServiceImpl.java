package ir.ipaam.kycservices.application.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.dto.AddressVerificationRequest;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.AddressVerificationService;
import ir.ipaam.kycservices.domain.command.CollectAddressCommand;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.model.entity.Address;
import ir.ipaam.kycservices.infrastructure.repository.AddressVerificationRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import ir.ipaam.kycservices.infrastructure.repository.KycStepStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.ADDRESS_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.POSTAL_CODE_INVALID;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.POSTAL_CODE_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressVerificationServiceImpl implements AddressVerificationService {

    private static final String STEP_ADDRESS_AND_ZIPCODE_COLLECTED = "ADDRESS_AND_ZIPCODE_COLLECTED";
    private static final String STEP_ZIPCODE_AND_ADDRESS_VALIDATED = "ZIPCODE_AND_ADDRESS_VALIDATED";

    private final CommandGateway commandGateway;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final KycStepStatusRepository kycStepStatusRepository;
    private final AddressVerificationRepository addressVerificationRepository;
    private final ZeebeClient zeebeClient;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${address.validation.base-url:http://192.168.179.21:8290}")
    private String validationBaseUrl;

    @Value("${address.validation.path:/api/transport/tipax/addresses/v1.0/address}")
    private String validationPath;

    @Value("${address.validation.stage:}")
    private String defaultStage;

    @Override
    public ResponseEntity<Map<String, Object>> collectAddress(AddressVerificationRequest request, String stageHeader) {
        String processInstanceId = normalizeProcessInstanceId(request.processInstanceId());
        String postalCode = normalizePostalCode(request.postalCode());
        String address = normalizeAddress(request.address());

        kycProcessInstanceRepository.findByCamundaInstanceId(processInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException(PROCESS_NOT_FOUND));

        Optional<Address> latestVerification = addressVerificationRepository
                .findTopByProcess_CamundaInstanceIdOrderByIdDesc(processInstanceId);

        boolean addressValidatedInProcess = kycStepStatusRepository
                .existsByProcess_CamundaInstanceIdAndStepName(
                        processInstanceId,
                        STEP_ZIPCODE_AND_ADDRESS_VALIDATED);

        boolean addressAlreadyValidated = latestVerification.map(Address::isZipValid).orElse(false)
                || addressValidatedInProcess;

        if (addressAlreadyValidated) {
            Address persisted = latestVerification.orElse(null);
            String persistedPostalCode = persisted != null ? persisted.getZipCode() : postalCode;
            String persistedAddress = persisted != null ? persisted.getAddress() : address;
            return buildConflictResponse(processInstanceId, persistedPostalCode, persistedAddress, true);
        }

        CollectAddressCommand command = new CollectAddressCommand(processInstanceId, postalCode, address);
        commandGateway.sendAndWait(command);

        publishWorkflowUpdate(
                "zip-code-and-address-collected",
                processInstanceId,
                postalCode,
                address,
                Map.of(
                        "kycStatus", STEP_ADDRESS_AND_ZIPCODE_COLLECTED,
                        "valid", false
                ));

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("processInstanceId", processInstanceId);
        responseBody.put("postalCode", postalCode);
        responseBody.put("address", address);
        responseBody.put("status", STEP_ADDRESS_AND_ZIPCODE_COLLECTED);

        Optional<Map<String, Object>> validationResult = validateWithExternalService(postalCode, address, stageHeader);
        validationResult.ifPresent(result -> {
            responseBody.put("validation", result);
            responseBody.put("validationStatus", STEP_ZIPCODE_AND_ADDRESS_VALIDATED);
        });

        validationResult.ifPresent(result -> {
            addressVerificationRepository
                    .findTopByProcess_CamundaInstanceIdOrderByIdDesc(processInstanceId)
                    .ifPresent(verification -> {
                        verification.setZipValid(true);
                        addressVerificationRepository.save(verification);
                    });

            commandGateway.sendAndWait(new UpdateKycStatusCommand(
                    processInstanceId,
                    STEP_ZIPCODE_AND_ADDRESS_VALIDATED,
                    STEP_ZIPCODE_AND_ADDRESS_VALIDATED,
                    "PASSED"
            ));

            Map<String, Object> variables = new HashMap<>();
            variables.put("kycStatus", STEP_ZIPCODE_AND_ADDRESS_VALIDATED);
            variables.put("valid", true);
            publishWorkflowUpdate(
                    "zipcode-and-address-validated",
                    processInstanceId,
                    postalCode,
                    address,
                    variables);
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);
    }

    private ResponseEntity<Map<String, Object>> buildConflictResponse(String processInstanceId,
                                                                       String postalCode,
                                                                       String address,
                                                                       boolean zipValidated) {
        Map<String, Object> body = new HashMap<>();
        body.put("processInstanceId", processInstanceId);
        body.put("postalCode", postalCode);
        body.put("address", address);
        if (zipValidated) {
            body.put("status", STEP_ZIPCODE_AND_ADDRESS_VALIDATED);
            body.put("validationStatus", STEP_ZIPCODE_AND_ADDRESS_VALIDATED);
            body.put("zipValid", true);
        } else {
            body.put("status", STEP_ADDRESS_AND_ZIPCODE_COLLECTED);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private Optional<Map<String, Object>> validateWithExternalService(String postalCode,
                                                                      String address,
                                                                      String stageHeader) {
        WebClient client = webClientBuilder.clone().baseUrl(validationBaseUrl).build();
        try {
            WebClient.RequestBodySpec requestSpec = client.post()
                    .uri(validationPath)
                    .contentType(MediaType.APPLICATION_JSON);

            String stageValue = StringUtils.hasText(stageHeader) ? stageHeader.trim() : defaultStage;
            if (StringUtils.hasText(stageValue)) {
                requestSpec = requestSpec.header("stage", stageValue);
            }

            JsonNode response = requestSpec
                    .bodyValue(Map.of(
                            "postalCode", postalCode,
                            "address", address
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Address validation response was empty for process postalCode {}", postalCode);
                return Optional.empty();
            }

            int statusCode = response.path("status").path("code").asInt(-1);
            if (statusCode != 200) {
                log.warn("Address validation failed with status code {} for postalCode {}", statusCode, postalCode);
                return Optional.empty();
            }

            Map<String, Object> converted = objectMapper.convertValue(
                    response,
                    new TypeReference<Map<String, Object>>() {
                    });
            return Optional.of(converted);
        } catch (WebClientResponseException ex) {
            log.error("Address validation service responded with error: {}", ex.getResponseBodyAsString(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.error("Address validation call failed", ex);
            return Optional.empty();
        }
    }

    private void publishWorkflowUpdate(String messageName,
                                       String processInstanceId,
                                       String postalCode,
                                       String address,
                                       Map<String, Object> additionalVariables) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("processInstanceId", processInstanceId);
        variables.put("postalCode", postalCode);
        variables.put("address", address);
        if (additionalVariables != null) {
            variables.putAll(additionalVariables);
        }

        zeebeClient.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(processInstanceId)
                .variables(variables)
                .send()
                .join();
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private String normalizePostalCode(String postalCode) {
        if (!StringUtils.hasText(postalCode)) {
            throw new IllegalArgumentException(POSTAL_CODE_REQUIRED);
        }
        String normalized = postalCode.replaceAll("[^0-9]", "").trim();
        if (normalized.length() != 10) {
            throw new IllegalArgumentException(POSTAL_CODE_INVALID);
        }
        return normalized;
    }

    private String normalizeAddress(String address) {
        if (!StringUtils.hasText(address)) {
            throw new IllegalArgumentException(ADDRESS_REQUIRED);
        }
        return address.trim();
    }
}

