package ir.ipaam.kycservices.domain.event;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class SaveTrackingNumberEvent {

    private String processInstanceId;
    private String trackingNumber;
    private LocalDateTime date;
    public SaveTrackingNumberEvent(String processInstanceId, String trackingNumber, LocalDateTime now) {
    }
}
