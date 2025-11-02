package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.List;

public record UploadBookletPagesCommand(
        @TargetAggregateIdentifier String processInstanceId,
        List<DocumentPayloadDescriptor> pageDescriptors
) {
}
