package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record UploadCardDocumentsCommand(
        @TargetAggregateIdentifier String processInstanceId,
        DocumentPayloadDescriptor frontDescriptor,
        DocumentPayloadDescriptor backDescriptor) {
}
