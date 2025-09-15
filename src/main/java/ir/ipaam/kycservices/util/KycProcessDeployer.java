package ir.ipaam.kycservices.util;

import io.camunda.zeebe.client.ZeebeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class KycProcessDeployer implements CommandLineRunner {

    private final ZeebeClient zeebeClient;

    @Override
    public void run(String... args) throws Exception {
        try (InputStream bpmnStream = getClass().getClassLoader()
                .getResourceAsStream("kyc-process.bpmn")) {

            if (bpmnStream == null) {
                throw new IllegalStateException("Cannot find BPMN file on classpath:kyc-process.bpmn");
            }

            zeebeClient.newDeployResourceCommand()
                    .addResourceStream(bpmnStream, "kyc-process.bpmn")
                    .send()
                    .join();

            System.out.println(" KYC process deployed successfully!");
        }
    }
}
