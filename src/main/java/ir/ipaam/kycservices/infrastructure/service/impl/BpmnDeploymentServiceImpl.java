package ir.ipaam.kycservices.infrastructure.service.impl;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Process;
import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import ir.ipaam.kycservices.infrastructure.repository.ProcessDeploymentRepository;
import ir.ipaam.kycservices.infrastructure.service.BpmnDeploymentService;
import ir.ipaam.kycservices.infrastructure.service.BpmnValidationService;
import ir.ipaam.kycservices.util.HashUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BpmnDeploymentServiceImpl implements BpmnDeploymentService {

    private final ZeebeClient zeebeClient;
    private final ProcessDeploymentRepository repository;
    private final BpmnValidationService validationService;

    @Transactional
    public ProcessDeployment deployIfChanged(InputStream bpmnStream) throws IOException {
        // Read BPMN to memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bpmnStream.transferTo(baos);
        byte[] data = baos.toByteArray();

        // 1. Validate BPMN & extract processId
        String processId;
        try (InputStream validationStream = new ByteArrayInputStream(data)) {
            BpmnModelInstance model = validationService.validateAndCheck(validationStream);
            processId = model.getModelElementsByType(Process.class)
                    .stream()
                    .findFirst()
                    .map(Process::getId)
                    .orElseThrow(() -> new IllegalArgumentException("No <bpmn:process> found in BPMN"));
        }

        // 2. Compute hash and check DB
        String newHash = HashUtils.sha256Hex(new ByteArrayInputStream(data));
        Optional<ProcessDeployment> existing = repository.findByFileHash(newHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 3. Deploy to Zeebe and capture result
        try (InputStream deployStream = new ByteArrayInputStream(data)) {
            var response = zeebeClient.newDeployResourceCommand()
                    .addResourceStream(deployStream, processId + ".bpmn")
                    .send()
                    .join();

            // Get first deployed process metadata (supports multiple resources but we deploy one)
            var process = response.getProcesses().get(0);

            ProcessDeployment deployment = new ProcessDeployment();
            deployment.setProcessId(process.getBpmnProcessId());
            deployment.setFileHash(newHash);
            deployment.setDeploymentKey(response.getKey());       // deployment key from Zeebe
            deployment.setProcessVersion(process.getVersion());   // version assigned by Zeebe
            deployment.setDeployedAt(LocalDateTime.now());

            return repository.save(deployment);
        }
    }

}
