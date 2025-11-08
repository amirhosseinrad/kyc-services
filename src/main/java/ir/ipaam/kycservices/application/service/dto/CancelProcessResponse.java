package ir.ipaam.kycservices.application.service.dto;

import java.time.LocalDateTime;

public record CancelProcessResponse(
        String processInstanceId,
        String status,
        LocalDateTime canceledAt
) {
}
