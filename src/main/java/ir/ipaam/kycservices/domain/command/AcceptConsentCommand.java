package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@AllArgsConstructor
public class AcceptConsentCommand {

    @TargetAggregateIdentifier
    private final String processInstanceId;
    private final String termsVersion;
    private final boolean accepted;
}
