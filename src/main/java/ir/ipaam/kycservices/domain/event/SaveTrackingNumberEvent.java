package ir.ipaam.kycservices.domain.event;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(force = true)
public class SaveTrackingNumberEvent {

    private final String processInstanceId;
    private final String trackingNumber;
    private final LocalDateTime date;

    public SaveTrackingNumberEvent(String processInstanceId, String trackingNumber, LocalDateTime now) {
        this.processInstanceId = processInstanceId;
        this.trackingNumber = trackingNumber;
        this.date = now;
    }
}
