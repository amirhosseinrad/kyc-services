package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordTrackingNumberCommand {
    private String trackingNumber;
    @TargetAggregateIdentifier
    private String processInstanceId;
    }
