package ir.ipaam.kycservices.domain.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ProvideEnglishPersonalInfoCommand(
        @TargetAggregateIdentifier
        String processInstanceId,
        String firstNameEn,
        String lastNameEn,
        String email,
        String telephone
) {
}
