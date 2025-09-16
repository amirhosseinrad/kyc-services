package ir.ipaam.kycservices.bootstarp;

import io.camunda.zeebe.client.ZeebeClient;
import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import ir.ipaam.kycservices.infrastructure.repository.ProcessDeploymentRepository;
import ir.ipaam.kycservices.util.HashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class KycProcessDeployer implements CommandLineRunner {
    private final ZeebeClient zeebeClient;
    private final ProcessDeploymentRepository deploymentRepo;

    @Override
    public void run(String... args) {
        // 1. Check if BPMN file exists — skip completely if not present
        InputStream fileCheck = getClass().getClassLoader().getResourceAsStream("bpmn/kyc-process.bpmn");
        if (fileCheck == null) {
            System.out.println("No BPMN file found (bpmn/kyc-process.bpmn). Skipping deployment.");
            return;
        }

        int attempts = 0;
        boolean deployed = false;

        while (attempts < 5 && !deployed) {
            try (InputStream bpmnStream = getClass().getClassLoader()
                    .getResourceAsStream("bpmn/kyc-process.bpmn")) {

                // Compute hash
                String newHash;
                try (InputStream hashStream = getClass().getClassLoader()
                        .getResourceAsStream("bpmn/kyc-process.bpmn")) {
                    newHash = HashUtils.sha256Hex(hashStream);
                }

                // Check DB for existing deployment by hash
                ProcessDeployment existing = deploymentRepo.findByFileHash(newHash).orElse(null);
                if (existing != null) {
                    System.out.println("KYC process already deployed with same hash, skipping.");
                    break;
                }

                // Deploy and get response from Zeebe
                var response = zeebeClient.newDeployResourceCommand()
                        .addResourceStream(bpmnStream, "bpmn/kyc-process.bpmn")
                        .send()
                        .join();

                var deployedProcess = response.getProcesses().get(0);

                // Save deployment info
                ProcessDeployment deployment = new ProcessDeployment();
                deployment.setProcessId(deployedProcess.getBpmnProcessId());
                deployment.setFileHash(newHash);
                deployment.setDeploymentKey(response.getKey());
                deployment.setProcessVersion(deployedProcess.getVersion());
                deployment.setDeployedAt(LocalDateTime.now());

                deploymentRepo.save(deployment);

                System.out.println("KYC process deployed successfully. Version " + deployedProcess.getVersion());
                deployed = true;

            } catch (Exception e) {
                attempts++;
                System.err.println("Failed to deploy KYC process (attempt " + attempts + "): " + e.getMessage());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
