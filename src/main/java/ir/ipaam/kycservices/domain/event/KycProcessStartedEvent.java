package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class KycProcessStartedEvent {
    private final String processInstanceId;
    private final String nationalCode;
    private final LocalDateTime startedAt;
}
