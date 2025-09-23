package ir.ipaam.kycservices.domain.command;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadCardDocumentsCommand{
    @TargetAggregateIdentifier String processInstanceId;
    DocumentPayloadDescriptor frontDescriptor;
    DocumentPayloadDescriptor backDescriptor;
}


