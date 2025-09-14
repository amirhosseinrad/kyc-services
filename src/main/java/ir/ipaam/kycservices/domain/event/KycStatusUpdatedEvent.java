package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KycStatusUpdatedEvent {
    private final String processInstanceId;
    private final String nationalCode;
    private final String status;
}
