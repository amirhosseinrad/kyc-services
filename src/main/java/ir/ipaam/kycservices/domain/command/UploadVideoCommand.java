package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UploadVideoCommand(
        @TargetAggregateIdentifier String processInstanceId,
        DocumentPayloadDescriptor videoDescriptor,
        String inquiryToken
) {
}
