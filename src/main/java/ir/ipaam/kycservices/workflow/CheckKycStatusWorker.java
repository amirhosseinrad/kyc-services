package ir.ipaam.kycservices.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.infrastructure.service.KycServiceTasks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckKycStatusWorker {

    private final KycServiceTasks kycServiceTasks;

    @JobWorker(type = "check-kyc-status")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String nationalCode = (String) variables.get("nationalCode");
        String status = kycServiceTasks.checkKycStatus(nationalCode);
        return Map.of("kycStatus", status);
    }
}
