package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckKycStatusWorker {

    private final QueryGateway queryGateway;

    @JobWorker(type = "check-kyc-status")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String processInstanceId = Long.toString(job.getProcessInstanceKey());
        String nationalCode = (String) variables.get("nationalCode");
        ProcessInstance instance = queryGateway.query(
                new FindKycStatusQuery(nationalCode),
                ResponseTypes.instanceOf(ProcessInstance.class))
                .join();

        Map<String, Object> response = new HashMap<>();
        response.put("processInstanceId", processInstanceId);

        String status = null;
        if (instance != null) {
            status = instance.getStatus();
        }

        if (status == null || status.isBlank() || "UNKNOWN".equalsIgnoreCase(status)) {
            status = "STARTED";
        }

        response.put("kycStatus", status);
        return response;
    }
}
