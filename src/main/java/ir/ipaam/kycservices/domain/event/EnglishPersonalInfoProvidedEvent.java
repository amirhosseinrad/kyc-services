package ir.ipaam.kycservices.domain.event;

import java.time.LocalDateTime;

public record EnglishPersonalInfoProvidedEvent(
        String processInstanceId,
        String nationalCode,
        String firstNameEn,
        String lastNameEn,
        String email,
        String telephone,
        LocalDateTime providedAt
) {
}
