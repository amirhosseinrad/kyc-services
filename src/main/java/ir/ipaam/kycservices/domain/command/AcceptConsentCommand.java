package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record AcceptConsentCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        String termsVersion,
        boolean accepted) {
}
