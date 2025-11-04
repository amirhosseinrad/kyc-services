package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.common.validation.IranianNationalCode;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record StartKycProcessCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        @IranianNationalCode
        String nationalCode) {
}
