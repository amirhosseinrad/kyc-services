package ir.ipaam.kycservices.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ConsentAcceptedEvent {
    private final String processInstanceId;
    private final String nationalCode;
    private final String termsVersion;
    private final boolean accepted;
    private final LocalDateTime acceptedAt;
}
