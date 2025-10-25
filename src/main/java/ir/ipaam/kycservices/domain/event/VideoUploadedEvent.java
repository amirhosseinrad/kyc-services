package ir.ipaam.kycservices.domain.event;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class VideoUploadedEvent {

    private final String processInstanceId;
    private final String nationalCode;
    private final DocumentPayloadDescriptor descriptor;
    private final LocalDateTime uploadedAt;
}
