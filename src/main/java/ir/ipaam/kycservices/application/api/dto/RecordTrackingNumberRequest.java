package ir.ipaam.kycservices.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordTrackingNumberRequest {
    private String trackingNumber;
    private String processInstanceNumber;
}
