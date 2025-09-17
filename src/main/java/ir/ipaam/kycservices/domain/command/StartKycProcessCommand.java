package ir.ipaam.kycservices.domain.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record StartKycProcessCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        String nationalCode) {
}
