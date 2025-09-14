package ir.ipaam.kycservices.application.workflow;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import ir.ipaam.kycservices.domain.query.FindKycStatusQuery;
import lombok.RequiredArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CheckKycStatusWorker {

    private final QueryGateway queryGateway;

    @JobWorker(type = "check-kyc-status")
    public Map<String, Object> handle(final ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        String nationalCode = (String) variables.get("nationalCode");
        SubscriptionQueryResult<String, String> result = queryGateway.subscriptionQuery(
                new FindKycStatusQuery(nationalCode),
                ResponseTypes.instanceOf(String.class),
                ResponseTypes.instanceOf(String.class));
        try {
            String initial = result.initialResult().join();
            if (!"UNKNOWN".equals(initial)) {
                return Map.of("kycStatus", initial);
            }
            String update = result.updates()
                    .filter(status -> !"UNKNOWN".equals(status))
                    .blockFirst();
            return Map.of("kycStatus", update);
        } finally {
            result.close();
        }
    }
}
