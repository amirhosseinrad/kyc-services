package ir.ipaam.kycservices.application.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.application.api.error.ResourceNotFoundException;
import ir.ipaam.kycservices.application.service.ProcessService;
import ir.ipaam.kycservices.domain.command.UpdateKycStatusCommand;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.model.entity.StepStatus;
import ir.ipaam.kycservices.infrastructure.repository.KycProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_INSTANCE_ID_REQUIRED;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.PROCESS_NOT_FOUND;
import static ir.ipaam.kycservices.application.api.error.ErrorMessageKeys.WORKFLOW_PROCESS_CANCEL_FAILED;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessServiceImpl implements ProcessService {

    private static final String STATUS_PROCESS_CANCELLED = "PROCESS_CANCELLED";

    private final ZeebeClient zeebeClient;
    private final KycProcessInstanceRepository kycProcessInstanceRepository;
    private final CommandGateway commandGateway;

    @Override
    public ResponseEntity<Map<String, Object>> cancelProcess(String processInstanceId) {
        String normalizedProcessId = normalizeProcessInstanceId(processInstanceId);
        ProcessInstance processInstance = kycProcessInstanceRepository.findByCamundaInstanceId(normalizedProcessId)
                .orElseThrow(() -> {
                    log.warn("Process instance with id {} not found", normalizedProcessId);
                    return new ResourceNotFoundException(PROCESS_NOT_FOUND);
                });

        long processKey = parseProcessInstanceKey(normalizedProcessId);

        try {
            zeebeClient.newCancelInstanceCommand(processKey).send().join();

        } catch (Exception ex) {
            log.error("Failed to cancel process instance {}", normalizedProcessId, ex);
            throw new IllegalArgumentException(WORKFLOW_PROCESS_CANCEL_FAILED, ex);
        }

        commandGateway.sendAndWait(new UpdateKycStatusCommand(
                normalizedProcessId,
                STATUS_PROCESS_CANCELLED,
                STATUS_PROCESS_CANCELLED,
                StepStatus.State.CANCELLED.name()
        ));

        LocalDateTime canceledAt = LocalDateTime.now();
        processInstance.setStatus(STATUS_PROCESS_CANCELLED);
        processInstance.setCompletedAt(canceledAt);
        kycProcessInstanceRepository.save(processInstance);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processInstanceId", normalizedProcessId);
        body.put("status", STATUS_PROCESS_CANCELLED);
        body.put("canceledAt", canceledAt);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    private String normalizeProcessInstanceId(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new IllegalArgumentException(PROCESS_INSTANCE_ID_REQUIRED);
        }
        return processInstanceId.trim();
    }

    private long parseProcessInstanceKey(String processInstanceId) {
        try {
            return Long.parseLong(processInstanceId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("processInstanceId must be a numeric value", ex);
        }
    }
}

