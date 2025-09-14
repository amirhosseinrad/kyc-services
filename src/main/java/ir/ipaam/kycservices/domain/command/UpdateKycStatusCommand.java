package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@AllArgsConstructor
public class UpdateKycStatusCommand {
    @TargetAggregateIdentifier
    private final String processInstanceId;
    private final String status;
}
