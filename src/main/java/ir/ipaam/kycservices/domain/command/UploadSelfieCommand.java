package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UploadSelfieCommand(
        @TargetAggregateIdentifier String processInstanceId,
        DocumentPayloadDescriptor selfieDescriptor
) {
}
