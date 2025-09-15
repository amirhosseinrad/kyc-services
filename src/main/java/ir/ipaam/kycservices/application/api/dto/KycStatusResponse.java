package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.domain.model.entity.KycProcessInstance;
import java.time.LocalDateTime;

public record KycStatusResponse(
        String camundaInstanceId,
        String status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        CustomerInfo customer,
        String error
) {
    public static KycStatusResponse success(KycProcessInstance instance) {
        if (instance == null) {
            return new KycStatusResponse(null, null, null, null, null, null);
        }
        return new KycStatusResponse(
                instance.getCamundaInstanceId(),
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                CustomerInfo.from(instance.getCustomer()),
                null
        );
    }

    public static KycStatusResponse error(String error) {
        return new KycStatusResponse(null, null, null, null, null, error);
    }
}
