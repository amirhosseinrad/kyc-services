package ir.ipaam.kycservices.application.api.dto;

import ir.ipaam.kycservices.domain.model.entity.StepStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record KycStepStatusDto(
        Long id,
        String stepName,
        StepStatus.State state,
        LocalDateTime timestamp
) {
    public static KycStepStatusDto from(StepStatus status) {
        if (status == null) {
            return null;
        }
        return new KycStepStatusDto(
                status.getId(),
                status.getStepName(),
                status.getState(),
                status.getTimestamp()
        );
    }

    public static List<KycStepStatusDto> from(List<StepStatus> statuses) {
        if (statuses == null) {
            return List.of();
        }
        return statuses.stream()
                .filter(Objects::nonNull)
                .map(KycStepStatusDto::from)
                .filter(Objects::nonNull)
                .toList();
    }
}

