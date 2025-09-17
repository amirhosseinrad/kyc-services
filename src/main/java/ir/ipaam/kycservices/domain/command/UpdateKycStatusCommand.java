package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UpdateKycStatusCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        String status,
        String stepName,
        String state) {
}
