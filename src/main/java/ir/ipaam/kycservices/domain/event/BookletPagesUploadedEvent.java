package ir.ipaam.kycservices.domain.event;

import ir.ipaam.kycservices.domain.model.value.DocumentPayloadDescriptor;

import java.time.LocalDateTime;
import java.util.List;

public record BookletPagesUploadedEvent(
        String processInstanceId,
        String nationalCode,
        List<DocumentPayloadDescriptor> pageDescriptors,
        LocalDateTime uploadedAt
) {
}
