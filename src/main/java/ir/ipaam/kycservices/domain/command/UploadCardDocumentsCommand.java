package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

@Data
@AllArgsConstructor
public class UploadCardDocumentsCommand {

    @TargetAggregateIdentifier
    private final String processInstanceId;
    private final DocumentPayloadDescriptor frontDescriptor;
    private final DocumentPayloadDescriptor backDescriptor;
}
