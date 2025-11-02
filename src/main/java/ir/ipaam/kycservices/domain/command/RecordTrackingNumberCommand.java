package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordTrackingNumberCommand {
    private String trackingNumber;
    private String processInstanceId;
    }
