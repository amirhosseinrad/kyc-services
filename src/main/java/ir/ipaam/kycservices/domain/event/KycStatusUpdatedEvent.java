package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class KycStatusUpdatedEvent {
    private final String processInstanceId;
    private final String nationalCode;
    private final String status;
    private final LocalDateTime updatedAt;
}
