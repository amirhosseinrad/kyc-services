package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import ir.ipaam.kycservices.domain.model.entity.ProcessInstance;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckKycStatusWorker {

    private final QueryGateway queryGateway;

    @JobWorker(type = "check-kyc-status")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String nationalCode = (String) variables.get("nationalCode");
        SubscriptionQueryResult<ProcessInstance, ProcessInstance> result = queryGateway.subscriptionQuery(
                new FindKycStatusQuery(nationalCode),
                ResponseTypes.instanceOf(ProcessInstance.class),
                ResponseTypes.instanceOf(ProcessInstance.class));
        try {
            ProcessInstance initial = result.initialResult().block();
            if (initial != null && !"UNKNOWN".equals(initial.getStatus())) {
                return Map.of("kycStatus", initial.getStatus());
            }
            ProcessInstance update = Flux.from(result.updates())
                    .filter(pi -> pi != null && !"UNKNOWN".equals(pi.getStatus()))
                    .blockFirst();
            return Map.of("kycStatus", update.getStatus());
        } finally {
            result.close();
        }
    }
}
