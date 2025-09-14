package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KycProcessStartedEvent {
    private final String processInstanceId;
    private final String nationalCode;
}
