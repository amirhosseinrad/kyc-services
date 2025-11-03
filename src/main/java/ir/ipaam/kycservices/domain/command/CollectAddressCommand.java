package ir.ipaam.kycservices.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CollectAddressCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        String postalCode,
        String address
) {
}

