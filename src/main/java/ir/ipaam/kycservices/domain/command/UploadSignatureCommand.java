package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UploadSignatureCommand(
        @TargetAggregateIdentifier String processInstanceId,
        DocumentPayloadDescriptor signatureDescriptor
) {
}
